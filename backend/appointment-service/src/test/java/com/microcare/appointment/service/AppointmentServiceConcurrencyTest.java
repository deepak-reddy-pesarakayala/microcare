package com.microcare.appointment.service;

import com.microcare.appointment.client.PatientServiceClient;
import com.microcare.appointment.dto.AppointmentResponse;
import com.microcare.appointment.dto.BookingRequest;
import com.microcare.appointment.dto.PatientResponse;
import com.microcare.appointment.dto.RescheduleRequest;
import com.microcare.appointment.entity.Appointment;
import com.microcare.appointment.entity.DoctorSlot;
import com.microcare.appointment.enums.AppointmentStatus;
import com.microcare.appointment.enums.SlotStatus;
import com.microcare.appointment.exception.SlotBookingConflictException;
import com.microcare.appointment.exception.SlotNotAvailableException;
import com.microcare.appointment.repository.AppointmentRepository;
import com.microcare.appointment.repository.DoctorSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AppointmentServiceConcurrencyTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private DoctorSlotRepository doctorSlotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private RedissonClient redissonClient;

    @MockBean
    private PatientServiceClient patientServiceClient;

    private DoctorSlot testSlot;
    private static final Long PATIENT_ID = 1L;
    private static final Long DOCTOR_ID = 1L;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        doctorSlotRepository.deleteAll();

        // Create a test slot with AVAILABLE status
        testSlot = doctorSlotRepository.save(DoctorSlot.builder()
                .doctorId(DOCTOR_ID)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .status(SlotStatus.AVAILABLE)
                .build());

        // Mock patient-service to always return a valid patient
        PatientResponse mockPatient = PatientResponse.builder()
                .id(PATIENT_ID)
                .fullName("Test Patient")
                .build();
        when(patientServiceClient.getPatientById(anyLong()))
                .thenReturn(ResponseEntity.ok(mockPatient));
    }

    @Test
    void testConcurrentBooking_OnlyOneShouldSucceed() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final long patientId = 100L + i; // Different patient IDs for each request
            tasks.add(() -> {
                try {
                    latch.await(); // All threads start at the same time
                    BookingRequest request = BookingRequest.builder()
                            .patientId(patientId)
                            .doctorId(DOCTOR_ID)
                            .slotId(testSlot.getId())
                            .build();

                    AppointmentResponse response = appointmentService.bookAppointment(request);
                    successCount.incrementAndGet();
                    System.out.printf("Thread %s: SUCCESS - Appointment %d booked for patient %d%n",
                            Thread.currentThread().getName(), response.getId(), patientId);
                } catch (SlotNotAvailableException | SlotBookingConflictException e) {
                    conflictCount.incrementAndGet();
                    System.out.printf("Thread %s: CONFLICT - %s%n",
                            Thread.currentThread().getName(), e.getMessage());
                } catch (Exception e) {
                    System.out.printf("Thread %s: ERROR - %s%n",
                            Thread.currentThread().getName(), e.getMessage());
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        latch.countDown(); // Fire all threads simultaneously

        // Wait for all futures to complete
        for (Future<Void> future : futures) {
            future.get();
        }

        executor.shutdown();

        // Assert exactly one booking succeeded
        assertThat(successCount.get())
                .as("Exactly one concurrent booking should succeed")
                .isEqualTo(1);

        // Assert 19 conflicts
        assertThat(conflictCount.get())
                .as("The remaining 19 requests should get conflicts")
                .isEqualTo(19);

        // Assert slot is now BOOKED
        DoctorSlot updatedSlot = doctorSlotRepository.findById(testSlot.getId()).orElseThrow();
        assertThat(updatedSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        // Assert only one appointment was created
        List<Appointment> appointments = appointmentRepository.findAll();
        assertThat(appointments).hasSize(1);
        assertThat(appointments.get(0).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointments.get(0).getSlotId()).isEqualTo(testSlot.getId());
    }

    @Test
    void testConcurrentBookingWithSamePatientId_OnlyOneShouldSucceed() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    latch.await();
                    BookingRequest request = BookingRequest.builder()
                            .patientId(PATIENT_ID) // Same patient for all threads
                            .doctorId(DOCTOR_ID)
                            .slotId(testSlot.getId())
                            .build();

                    AppointmentResponse response = appointmentService.bookAppointment(request);
                    successCount.incrementAndGet();
                } catch (SlotNotAvailableException | SlotBookingConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.printf("Thread %s: ERROR - %s%n",
                            Thread.currentThread().getName(), e.getMessage());
                }
                return null;
            });
        }

        // Release the latch BEFORE invokeAll: the tasks block on latch.await(), so
        // invokeAll would deadlock if the latch were released afterwards.
        latch.countDown();
        List<Future<Void>> futures = executor.invokeAll(tasks);

        for (Future<Void> future : futures) {
            future.get();
        }

        executor.shutdown();

        assertThat(successCount.get())
                .as("Exactly one concurrent booking should succeed even with same patient ID")
                .isEqualTo(1);

        assertThat(conflictCount.get())
                .as("The remaining 19 requests should get conflicts")
                .isEqualTo(19);
    }

    @Test
    void testBookThenReschedule_ShouldSucceed() {
        // First book the slot
        BookingRequest bookingRequest = BookingRequest.builder()
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .slotId(testSlot.getId())
                .build();

        AppointmentResponse bookingResponse = appointmentService.bookAppointment(bookingRequest);
        assertThat(bookingResponse.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

        // Create a second slot
        DoctorSlot newSlot = doctorSlotRepository.save(DoctorSlot.builder()
                .doctorId(DOCTOR_ID)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .status(SlotStatus.AVAILABLE)
                .build());

        // Reschedule to new slot
        RescheduleRequest rescheduleRequest = RescheduleRequest.builder()
                .newSlotId(newSlot.getId())
                .build();

        AppointmentResponse rescheduleResponse = appointmentService.rescheduleAppointment(
                bookingResponse.getId(), rescheduleRequest);

        assertThat(rescheduleResponse.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(rescheduleResponse.getSlotId()).isEqualTo(newSlot.getId());

        // Old slot should be AVAILABLE again
        DoctorSlot oldSlot = doctorSlotRepository.findById(testSlot.getId()).orElseThrow();
        assertThat(oldSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);

        // New slot should be BOOKED
        DoctorSlot bookedNewSlot = doctorSlotRepository.findById(newSlot.getId()).orElseThrow();
        assertThat(bookedNewSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void testBookThenCancel_ShouldSucceed() {
        // First book the slot
        BookingRequest bookingRequest = BookingRequest.builder()
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .slotId(testSlot.getId())
                .build();

        AppointmentResponse bookingResponse = appointmentService.bookAppointment(bookingRequest);
        assertThat(bookingResponse.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

        // Cancel the appointment
        AppointmentResponse cancelResponse = appointmentService.cancelAppointment(bookingResponse.getId());
        assertThat(cancelResponse.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        // Slot should be AVAILABLE again
        DoctorSlot freedSlot = doctorSlotRepository.findById(testSlot.getId()).orElseThrow();
        assertThat(freedSlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void testBookSlot_WhenSlotAlreadyBooked_ShouldThrowConflict() {
        // First booking succeeds
        BookingRequest request = BookingRequest.builder()
                .patientId(PATIENT_ID)
                .doctorId(DOCTOR_ID)
                .slotId(testSlot.getId())
                .build();

        appointmentService.bookAppointment(request);

        // Second booking should fail
        BookingRequest secondRequest = BookingRequest.builder()
                .patientId(999L)
                .doctorId(DOCTOR_ID)
                .slotId(testSlot.getId())
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                SlotNotAvailableException.class,
                () -> appointmentService.bookAppointment(secondRequest)
        );
    }
}
