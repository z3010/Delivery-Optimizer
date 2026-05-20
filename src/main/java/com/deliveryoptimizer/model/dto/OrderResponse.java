package com.deliveryoptimizer.model.dto;

import com.deliveryoptimizer.model.enums.OrderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record OrderResponse(
    Long id,
    String externalRef,
    Long zoneId,
    String zoneName,
    Long slotId,
    LocalDate slotDate,
    LocalTime windowStart,
    LocalTime windowEnd,
    String customerName,
    String deliveryAddress,
    OrderStatus status,
    Instant placedAt,
    Instant assignedAt
) {}
