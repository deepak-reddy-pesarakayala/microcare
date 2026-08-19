package com.microcare.appointment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microcare.appointment.client.PatientServiceClient;
import com.microcare.common.CorrelationId;
import com.microcare.appointment.dto.AppointmentResponse;
import com.microcare.appointment.dto.BookingRequest;
import com.microcare.appointment.dto.RescheduleRequest;
import com.microcare.appointment.dto.SlotRequest;
import com.microcare.appointment.dto.SlotResponse;
import com.microcare.appointment.entity.Appointment;
import com.microcare.appointment.entity.DoctorSlot;
import com.microcare.appointment.entity.OutboxEvent;
import com.microcare.appointment.enums.AppointmentStatus;
import com.microcare.appointment.enums.SlotStatus;
import com.microcare.appointment.exception.ResourceNotFoundException;
import com.microcare.appointment.exception.SlotBookingConflictException;
import com.microcare.appointment.exception.SlotNotAvailableException;
import feign.FeignException;
import com.microcare.appointment.repository.AppointmentRepository;
import com.microcare.appointment.repository.DoctorSlotRepository;
import com.microcare.appointment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final String LOCK_KEY_PREFIX = "slot:lock:";
    private static final long TRY_LOCK_WAIT_TIME_MS = 3000;
    private static final long LOCK_LEASE_TIME_MS = 5000;
    private static final int MAX_RETRIES = 3;

    private final AppointmentRepository appointmentRepository;
    private final DoctorSlotRepository doctorSlotRepository;
    private final PatientServiceClient patientServiceClient;
    private final RedissonClient redissonClient;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Shared ObjectMapper for creating outbox event payloads.
     * Registered with JavaTimeModule for proper LocalDateTime serialization.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Books an appointment with distributed locking and optimistic concurrency control.
     * Validates patient exists via Feign client, acquires Redisson lock on slotId,
     * checks slot availability, marks slot as BOOKED (with @Version check),
     * and retries up to 3 times on OptimisticLockException.
     */
    /**
     * Books an appointment and writes an OutboxEvent in the SAME transaction.
     *
     * WHY THIS ELIMINATES THE DUAL-WRITE PROBLEM:
     * --------------------------------------------
     * The appointment status update AND the outbox event insert happen within
     * the same @Transactional boundary. If the transaction succeeds, both are
     * committed atomically. If it fails, both are rolled back. This guarantees
     * that we never have a confirmed appointment without a corresponding outbox
     * event (or vice versa).
     *
     * The outbox event is then picked up asynchronously by OutboxEventPublisher
     * (a @Scheduled poller) and published to RabbitMQ. This decouples the
     * transaction commit from the message broker, eliminating the dual-write
     * problem entirely.
     */
    @Transactional
    public AppointmentResponse bookAppointment(BookingRequest request) {
        return bookAppointment(request, null);
    }

    /**
     * Books an appointment, additionally enforcing that a PATIENT caller
     * (identified by {@code callerPatientId}) only books for themselves.
     */
    @Transactional
    public AppointmentResponse bookAppointment(BookingRequest request, Long callerPatientId) {
        if (callerPatientId != null && !callerPatientId.equals(request.getPatientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Patients may only book appointments for themselves");
        }
        validatePatientExists(request.getPatientId());

        return executeWithLockAndRetry(request.getSlotId(), () -> {
            // Check slot availability via optimistic lock read
            DoctorSlot slot = doctorSlotRepository.findByIdWithOptimisticLock(request.getSlotId())
                    .orElseThrow(() -> new ResourceNotFoundException("DoctorSlot", "id", request.getSlotId()));

            if (slot.getStatus() != SlotStatus.AVAILABLE) {
                throw new SlotNotAvailableException(request.getSlotId());
            }

            // A slot can only be booked for the doctor it belongs to.
            if (!slot.getDoctorId().equals(request.getDoctorId())) {
                throw new IllegalArgumentException("Slot " + request.getSlotId()
                        + " belongs to doctor " + slot.getDoctorId()
                        + " but the request specified doctor " + request.getDoctorId());
            }

            // Mark slot as BOOKED — @Version will detect concurrent modifications.
            // flush() surfaces the version conflict INSIDE the retry loop so that
            // the SlotBookingConflictException path actually engages (otherwise the
            // stale-state failure surfaces at tx commit, outside the retry).
            slot.setStatus(SlotStatus.BOOKED);
            doctorSlotRepository.save(slot);
            doctorSlotRepository.flush();

            // Create the booking request. It stays PENDING (slot reserved) until
            // the doctor accepts it — only then is it CONFIRMED and an outbox
            // event written (billing/invoice). If the doctor rejects it, the
            // slot is released again.
            Appointment appointment = Appointment.builder()
                    .patientId(request.getPatientId())
                    .doctorId(request.getDoctorId())
                    .slotId(request.getSlotId())
                    .status(AppointmentStatus.PENDING)
                    .build();

            Appointment saved = appointmentRepository.save(appointment);
            log.info("Appointment booked (pending doctor approval): id={}, slotId={}, patientId={}",
                    saved.getId(), saved.getSlotId(), saved.getPatientId());

            return mapToResponse(saved);
        });
    }

    /**
     * Accepts a PENDING booking request: flips it to CONFIRMED and writes the
     * transactional outbox event (which triggers invoice creation downstream).
     *
     * <p>When {@code callerDoctorId} is provided (a DOCTOR-role caller), the
     * doctor may only accept appointments assigned to them.
     */
    @Transactional
    public AppointmentResponse acceptAppointment(Long appointmentId, Long callerDoctorId) {
        Appointment appointment = requirePendingForDoctor(appointmentId, callerDoctorId);

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment accepted: id={}, slotId={}", saved.getId(), saved.getSlotId());

        // ---- TRANSACTIONAL OUTBOX: write event in the SAME transaction ----
        writeOutboxEvent(saved);

        return mapToResponse(saved);
    }

    /**
     * Rejects a PENDING booking request: marks it REJECTED and releases the
     * reserved slot back to AVAILABLE so the patient (or another patient) can
     * book it again.
     *
     * <p>When {@code callerDoctorId} is provided (a DOCTOR-role caller), the
     * doctor may only reject appointments assigned to them.
     */
    @Transactional
    public AppointmentResponse rejectAppointment(Long appointmentId, Long callerDoctorId) {
        Appointment appointment = requirePendingForDoctor(appointmentId, callerDoctorId);

        Long slotId = appointment.getSlotId();

        // Lock and release the reserved slot back to AVAILABLE.
        executeWithLockAndRetry(slotId, () -> {
            DoctorSlot slot = doctorSlotRepository.findByIdWithOptimisticLock(slotId)
                    .orElseThrow(() -> new ResourceNotFoundException("DoctorSlot", "id", slotId));

            slot.setStatus(SlotStatus.AVAILABLE);
            doctorSlotRepository.save(slot);
            doctorSlotRepository.flush();
            return null;
        });

        appointment.setStatus(AppointmentStatus.REJECTED);
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment rejected: id={}, slotId={}", saved.getId(), slotId);
        return mapToResponse(saved);
    }

    /**
     * Loads a PENDING appointment, enforcing that a DOCTOR-role caller may only
     * act on their own appointments.
     */
    private Appointment requirePendingForDoctor(Long appointmentId, Long callerDoctorId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (callerDoctorId != null && !callerDoctorId.equals(appointment.getDoctorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Doctors may only accept or reject their own appointments");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Only PENDING appointments can be accepted or rejected");
        }
        return appointment;
    }

    /**
     * Reschedules an appointment: releases old slot, books new slot with locking.
     */
    @Transactional
    public AppointmentResponse rescheduleAppointment(Long appointmentId, RescheduleRequest request) {
        return rescheduleAppointment(appointmentId, request, null);
    }

    /**
     * Reschedules an appointment, additionally enforcing that a PATIENT caller
     * (identified by {@code callerPatientId}) only touches their own appointment.
     */
    @Transactional
    public AppointmentResponse rescheduleAppointment(Long appointmentId, RescheduleRequest request,
                                                     Long callerPatientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (callerPatientId != null && !callerPatientId.equals(appointment.getPatientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Patients may only reschedule their own appointments");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot reschedule a cancelled appointment");
        }

        Long oldSlotId = appointment.getSlotId();
        Long newSlotId = request.getNewSlotId();

        // Validate patient still exists
        validatePatientExists(appointment.getPatientId());

        // Lock and book new slot
        executeWithLockAndRetry(newSlotId, () -> {
            DoctorSlot newSlot = doctorSlotRepository.findByIdWithOptimisticLock(newSlotId)
                    .orElseThrow(() -> new ResourceNotFoundException("DoctorSlot", "id", newSlotId));

            if (newSlot.getStatus() != SlotStatus.AVAILABLE) {
                throw new SlotNotAvailableException(newSlotId);
            }

            // The new slot must belong to the same doctor as the appointment.
            if (!newSlot.getDoctorId().equals(appointment.getDoctorId())) {
                throw new IllegalArgumentException("Cannot reschedule to a slot of a different doctor");
            }

            newSlot.setStatus(SlotStatus.BOOKED);
            doctorSlotRepository.save(newSlot);
            doctorSlotRepository.flush();
            return null;
        });

        // Lock and release old slot
        executeWithLockAndRetry(oldSlotId, () -> {
            DoctorSlot oldSlot = doctorSlotRepository.findByIdWithOptimisticLock(oldSlotId)
                    .orElseThrow(() -> new ResourceNotFoundException("DoctorSlot", "id", oldSlotId));

            oldSlot.setStatus(SlotStatus.AVAILABLE);
            doctorSlotRepository.save(oldSlot);
            doctorSlotRepository.flush();
            return null;
        });

        // Update appointment
        appointment.setSlotId(newSlotId);
        appointment.setStatus(AppointmentStatus.RESCHEDULED);
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment rescheduled: id={}, oldSlotId={}, newSlotId={}",
                saved.getId(), oldSlotId, newSlotId);
        return mapToResponse(saved);
    }

    /**
     * Cancels an appointment and frees the slot.
     */
    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId) {
        return cancelAppointment(appointmentId, null);
    }

    /**
     * Cancels an appointment, additionally enforcing that a PATIENT caller
     * (identified by {@code callerPatientId}) only touches their own appointment.
     */
    @Transactional
    public AppointmentResponse cancelAppointment(Long appointmentId, Long callerPatientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (callerPatientId != null && !callerPatientId.equals(appointment.getPatientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Patients may only cancel their own appointments");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Appointment is already cancelled");
        }

        Long slotId = appointment.getSlotId();

        // Lock and free the slot
        executeWithLockAndRetry(slotId, () -> {
            DoctorSlot slot = doctorSlotRepository.findByIdWithOptimisticLock(slotId)
                    .orElseThrow(() -> new ResourceNotFoundException("DoctorSlot", "id", slotId));

            slot.setStatus(SlotStatus.AVAILABLE);
            doctorSlotRepository.save(slot);
            doctorSlotRepository.flush();
            return null;
        });

        // Update appointment status
        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment cancelled: id={}, slotId={}", saved.getId(), slotId);
        return mapToResponse(saved);
    }

    /**
     * Executes the given callback within a Redisson distributed lock for the slot,
     * retrying up to MAX_RETRIES times on OptimisticLockException.
     */
    private <T> T executeWithLockAndRetry(Long slotId, LockedOperation<T> operation) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + slotId);
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            boolean locked = false;
            try {
                locked = lock.tryLock(TRY_LOCK_WAIT_TIME_MS, LOCK_LEASE_TIME_MS, TimeUnit.MILLISECONDS);
                if (!locked) {
                    log.warn("Could not acquire distributed lock for slot {} after {}ms (attempt {}/{})",
                            slotId, TRY_LOCK_WAIT_TIME_MS, attempt, MAX_RETRIES);
                    continue;
                }

                try {
                    return operation.execute();
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("Optimistic lock failure for slot {} (attempt {}/{}): {}",
                            slotId, attempt, MAX_RETRIES, e.getMessage());
                    lastException = new SlotBookingConflictException(slotId);
                    // Sleep a bit before retrying to let the other transaction complete
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(100 * attempt);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SlotBookingConflictException("Operation was interrupted");
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        log.error("All {} retry attempts exhausted for slot {}", MAX_RETRIES, slotId);
        throw lastException != null
                ? lastException
                : new SlotBookingConflictException(slotId);
    }

    /**
     * Validates that the patient exists by calling patient-service via Feign client.
     * Gracefully degrades if the downstream service is unavailable.
     */
    private void validatePatientExists(Long patientId) {
        try {
            ResponseEntity<?> response = patientServiceClient.getPatientById(patientId);
            HttpStatus statusCode = (HttpStatus) response.getStatusCode();

            // Fallback returns SERVICE_UNAVAILABLE — treat as degraded, not a missing patient
            if (statusCode == HttpStatus.SERVICE_UNAVAILABLE) {
                log.warn("patient-service is unavailable, proceeding without patient validation for ID: {}", patientId);
                return;
            }

            if (statusCode == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Patient", "id", patientId);
            }

            if (response.getBody() == null) {
                log.warn("patient-service returned empty body for patient ID: {}. Proceeding.", patientId);
            }
        } catch (ResourceNotFoundException e) {
            // Re-throw patient not found
            throw e;
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Patient", "id", patientId);
            }
            log.warn("Feign call to patient-service returned {}: {}. Proceeding.",
                    e.getStatusCode(), e.getMessage());
        } catch (FeignException.NotFound e) {
            // A genuine 404 from patient-service must reject the booking
            // (covers the case where the custom ErrorDecoder is not applied
            // and the raw FeignException surfaces).
            throw new ResourceNotFoundException("Patient", "id", patientId);
        } catch (FeignException e) {
            if (e.status() == HttpStatus.NOT_FOUND.value()) {
                throw new ResourceNotFoundException("Patient", "id", patientId);
            }
            // Other Feign errors (5xx, transport) should not block booking
            log.warn("Feign call to patient-service failed (status {}): {}. Proceeding.",
                    e.status(), e.getMessage());
        } catch (RuntimeException e) {
            // Feign transport errors (timeouts, circuit breaker) should not block booking
            log.warn("Could not validate patient {} due to: {}. Proceeding with booking.",
                    patientId, e.getMessage());
        }
    }

    // =====================
    // Query + slot management (used by the frontend)
    // =====================

    /**
     * Lists appointments, optionally filtered by patient or doctor.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(Long patientId, Long doctorId) {
        return getAppointments(patientId, doctorId, null);
    }

    /**
     * Lists appointments, additionally enforcing that a PATIENT caller
     * (identified by {@code callerPatientId}) may only list their own records.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(Long patientId, Long doctorId, Long callerPatientId) {
        if (callerPatientId != null && !callerPatientId.equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Patients may only list their own appointments");
        }
        List<Appointment> appointments;
        if (patientId != null) {
            appointments = appointmentRepository.findByPatientId(patientId);
        } else if (doctorId != null) {
            appointments = appointmentRepository.findByDoctorId(doctorId);
        } else {
            appointments = appointmentRepository.findAll();
        }
        return appointments.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves a single appointment by ID.
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
        return mapToResponse(appointment);
    }

    /**
     * Lists AVAILABLE slots, optionally filtered by doctor.
     */
    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(Long doctorId) {
        List<DoctorSlot> slots;
        if (doctorId != null) {
            slots = doctorSlotRepository.findByDoctorId(doctorId).stream()
                    .filter(s -> s.getStatus() == SlotStatus.AVAILABLE)
                    .toList();
        } else {
            slots = doctorSlotRepository.findByStatus(SlotStatus.AVAILABLE);
        }
        return slots.stream()
                .sorted((a, b) -> {
                    int byDate = a.getSlotDate().compareTo(b.getSlotDate());
                    return byDate != 0 ? byDate : a.getStartTime().compareTo(b.getStartTime());
                })
                .map(this::mapToSlotResponse)
                .toList();
    }

    /**
     * Creates a new AVAILABLE slot for a doctor.
     */
    @Transactional
    public SlotResponse createSlot(SlotRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        DoctorSlot slot = DoctorSlot.builder()
                .doctorId(request.getDoctorId())
                .slotDate(request.getSlotDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SlotStatus.AVAILABLE)
                .build();
        DoctorSlot saved = doctorSlotRepository.save(slot);
        log.info("Slot created: id={}, doctorId={}, date={} {} - {}",
                saved.getId(), saved.getDoctorId(), saved.getSlotDate(),
                saved.getStartTime(), saved.getEndTime());
        return mapToSlotResponse(saved);
    }

    private SlotResponse mapToSlotResponse(DoctorSlot slot) {
        return SlotResponse.builder()
                .id(slot.getId())
                .doctorId(slot.getDoctorId())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .build();
    }

    private AppointmentResponse mapToResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .slotId(appointment.getSlotId())
                .status(appointment.getStatus())
                .createdAt(appointment.getCreatedAt())
                .build();
    }

    /**
     * Writes an OutboxEvent for the given appointment in the CURRENT transaction.
     *
     * The payload contains the minimum data needed by the billing-service to create an invoice:
     * appointmentId, patientId, and a default amount (can be customized later).
     *
     * A UUID messageId is generated for idempotent consumption downstream.
     */
    private void writeOutboxEvent(Appointment appointment) {
        try {
            Map<String, Object> payload = Map.of(
                    "appointmentId", appointment.getId(),
                    "patientId", appointment.getPatientId(),
                    "doctorId", appointment.getDoctorId(),
                    "amount", 100.00, // Default consultation fee — can be dynamic in future
                    "currency", "USD",
                    "appointmentDate", appointment.getCreatedAt().toString()
            );

            String payloadJson = MAPPER.writeValueAsString(payload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .messageId(UUID.randomUUID().toString())
                    .aggregateType("appointment")
                    .aggregateId(appointment.getId())
                    .eventType("APPOINTMENT_CONFIRMED")
                    .payload(payloadJson)
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .correlationId(CorrelationId.get())
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event written: aggregateType=appointment, aggregateId={}, eventType=APPOINTMENT_CONFIRMED",
                    appointment.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for appointment {}: {}",
                    appointment.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }

    @FunctionalInterface
    private interface LockedOperation<T> {
        T execute();
    }
}
