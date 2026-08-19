package com.microcare.appointment.exception;

public class SlotNotAvailableException extends RuntimeException {

    public SlotNotAvailableException(Long slotId) {
        super(String.format("Slot with ID '%s' is not available for booking", slotId));
    }
}
