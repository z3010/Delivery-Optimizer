package com.deliveryoptimizer.exception;

import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorBody(String error, String message, Instant timestamp) {}

    @ExceptionHandler(ZoneNotFoundException.class)
    public ResponseEntity<ErrorBody> handleZoneNotFound(ZoneNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorBody("ZONE_NOT_FOUND", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(SlotUnavailableException.class)
    public ResponseEntity<ErrorBody> handleSlotUnavailable(SlotUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorBody("SLOT_UNAVAILABLE", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                (a, b) -> a
            ));
        return ResponseEntity.badRequest().body(Map.of(
            "error", "VALIDATION_FAILED",
            "fields", fieldErrors,
            "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorBody("BAD_REQUEST", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
            .body(new ErrorBody("INTERNAL_ERROR", "An unexpected error occurred", Instant.now()));
    }
}
