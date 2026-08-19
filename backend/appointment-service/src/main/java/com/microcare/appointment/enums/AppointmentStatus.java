package com.microcare.appointment.enums;

public enum AppointmentStatus {
    /**
     * Booking request created by a patient — waiting for the doctor to accept
     * or reject it. The slot is reserved (BOOKED) meanwhile.
     */
    PENDING,
    CONFIRMED,
    REJECTED,
    RESCHEDULED,
    CANCELLED
}
