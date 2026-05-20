package com.deliveryoptimizer.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BulkSimulateRequest(

    @NotNull @Min(1) @Max(500)
    Integer orderCount,

    Long zoneId,            // null = distribute across all active zones

    Boolean randomZones,    // true = pick zone randomly per order
    Integer delayMs         // artificial delay between orders (0 = no delay)
) {}
