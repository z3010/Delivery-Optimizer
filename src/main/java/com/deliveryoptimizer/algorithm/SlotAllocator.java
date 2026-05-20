package com.deliveryoptimizer.algorithm;

import com.deliveryoptimizer.model.entity.DeliverySlot;
import com.deliveryoptimizer.model.entity.DeliveryZone;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Strategy interface for slot allocation.
 * Swap implementations without touching service code.
 */
public interface SlotAllocator {

    /**
     * Find the best available slot for an order in a given zone on a given date.
     *
     * @param zone        the delivery zone
     * @param targetDate  preferred delivery date
     * @return the chosen slot, or empty if none available
     */
    Optional<DeliverySlot> allocate(DeliveryZone zone, LocalDate targetDate);
}
