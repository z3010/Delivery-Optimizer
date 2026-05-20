package com.deliveryoptimizer.exception;

public class ZoneNotFoundException extends RuntimeException {
    public ZoneNotFoundException(Long id) {
        super("Delivery zone not found: " + id);
    }
}
