package com.deliveryoptimizer.model.dto;

import com.deliveryoptimizer.model.enums.ZoneStatus;

public record ZoneLoadResponse(
    Long zoneId,
    String zoneName,
    String city,
    ZoneStatus status,
    int totalVehicles,
    int activeVehicles,
    long activeOrders,
    long availableSlots,
    double loadPercent        // activeOrders / total capacity * 100
) {}
