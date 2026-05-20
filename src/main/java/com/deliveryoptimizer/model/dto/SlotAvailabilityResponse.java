package com.deliveryoptimizer.model.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotAvailabilityResponse(
    Long slotId,
    Long zoneId,
    String zoneName,
    LocalDate slotDate,
    LocalTime windowStart,
    LocalTime windowEnd,
    int capacity,
    int bookedCount,
    int remaining,
    double fillRate,
    boolean available
) {}
