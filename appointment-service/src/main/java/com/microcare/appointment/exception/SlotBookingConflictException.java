package com.microcare.appointment.exception;

public class SlotBookingConflictException extends RuntimeException {

    public SlotBookingConflictException(Long slotId) {
        super(String.format("Concurrent booking conflict for slot ID '%s'. Please try again.", slotId));
    }

    public SlotBookingConflictException(String message) {
        super(message);
    }
}
