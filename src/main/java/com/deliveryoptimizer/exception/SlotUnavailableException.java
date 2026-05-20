package com.deliveryoptimizer.exception;

public class SlotUnavailableException extends RuntimeException {
    public SlotUnavailableException(Long zoneId) {
        super("No available delivery slots for zone: " + zoneId);
    }

    public SlotUnavailableException(String message) {
        super(message);
    }
}
