package com.microcare.appointment.controller;

import com.microcare.appointment.dto.AppointmentResponse;
import com.microcare.appointment.dto.BookingRequest;
import com.microcare.appointment.dto.RescheduleRequest;
import com.microcare.appointment.dto.SlotRequest;
import com.microcare.appointment.dto.SlotResponse;
import com.microcare.appointment.service.AppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    /**
     * Identity headers injected by the API gateway after JWT validation.
     * {@code X-User-Role} is the role from the token, {@code X-User-Patient-Id}
     * is the {@code pid} claim (only set for PATIENT) and
     * {@code X-User-Doctor-Id} is the {@code did} claim (only set for DOCTOR).
     */
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_PATIENT_ID = "X-User-Patient-Id";
    private static final String HEADER_USER_DOCTOR_ID = "X-User-Doctor-Id";
    private static final String ROLE_PATIENT = "PATIENT";
    private static final String ROLE_DOCTOR = "DOCTOR";

    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAppointments(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                appointmentService.getAppointments(patientId, doctorId, callerPatientId(httpRequest)));
    }

    @GetMapping("/slots")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(
            @RequestParam(required = false) Long doctorId) {
        return ResponseEntity.ok(appointmentService.getAvailableSlots(doctorId));
    }

    @PostMapping("/slots")
    public ResponseEntity<SlotResponse> createSlot(@Valid @RequestBody SlotRequest request) {
        SlotResponse response = appointmentService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointment(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.getAppointment(id));
    }

    @PostMapping("/book")
    public ResponseEntity<AppointmentResponse> bookAppointment(
            @Valid @RequestBody BookingRequest request,
            HttpServletRequest httpRequest) {
        AppointmentResponse response =
                appointmentService.bookAppointment(request, callerPatientId(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/appointments/{id}/accept — a doctor accepts a PENDING booking
     * request (ADMIN may also accept). The appointment becomes CONFIRMED and an
     * invoice is created via the outbox.
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<AppointmentResponse> acceptAppointment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        AppointmentResponse response = appointmentService.acceptAppointment(id, callerDoctorId(httpRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/appointments/{id}/reject — a doctor rejects a PENDING booking
     * request (ADMIN may also reject). The appointment becomes REJECTED and the
     * slot is released back to AVAILABLE.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<AppointmentResponse> rejectAppointment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        AppointmentResponse response = appointmentService.rejectAppointment(id, callerDoctorId(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> rescheduleAppointment(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request,
            HttpServletRequest httpRequest) {
        AppointmentResponse response =
                appointmentService.rescheduleAppointment(id, request, callerPatientId(httpRequest));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        AppointmentResponse response = appointmentService.cancelAppointment(id, callerPatientId(httpRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the {@code did} claim of the caller when they are a DOCTOR-role
     * user, otherwise {@code null}. ADMIN callers may act on any doctor's
     * appointments.
     */
    private Long callerDoctorId(HttpServletRequest httpRequest) {
        if (!ROLE_DOCTOR.equals(httpRequest.getHeader(HEADER_USER_ROLE))) {
            return null;
        }
        String did = httpRequest.getHeader(HEADER_USER_DOCTOR_ID);
        if (did == null || did.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Missing doctor identity claim");
        }
        try {
            return Long.parseLong(did);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Invalid doctor identity claim");
        }
    }

    /**
     * Returns the {@code pid} claim of the caller when they are a PATIENT-role
     * user, otherwise {@code null}. Staff callers (ADMIN/DOCTOR) are not bound
     * to a single patient record and may act on behalf of any patient.
     */
    private Long callerPatientId(HttpServletRequest httpRequest) {
        if (!ROLE_PATIENT.equals(httpRequest.getHeader(HEADER_USER_ROLE))) {
            return null;
        }
        String pid = httpRequest.getHeader(HEADER_USER_PATIENT_ID);
        if (pid == null || pid.isBlank()) {
            // A PATIENT claim without a patient id must never fall back to "no
            // restriction" — reject instead.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Missing patient identity claim");
        }
        try {
            return Long.parseLong(pid);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Invalid patient identity claim");
        }
    }
}
