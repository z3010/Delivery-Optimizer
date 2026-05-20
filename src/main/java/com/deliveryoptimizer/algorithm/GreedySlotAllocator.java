package com.deliveryoptimizer.algorithm;

import com.deliveryoptimizer.config.AppProperties;
import com.deliveryoptimizer.model.entity.DeliverySlot;
import com.deliveryoptimizer.model.entity.DeliveryZone;
import com.deliveryoptimizer.repository.DeliverySlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Greedy constraint-based slot allocator.
 *
 * Strategy:
 *   1. Fetch all available slots for the zone + date from DB.
 *   2. Filter: slot must have remaining capacity AND zone must not be overloaded.
 *   3. Sort by fill-rate DESC — prefer slots already partially filled to pack them
 *      before opening new windows (bin-packing intuition).
 *   4. Apply fleet constraint: skip slots whose window-level concurrent load
 *      would exceed the zone's active vehicle throughput.
 *   5. Return the best candidate.
 *
 * This avoids under-utilised slots sprawling across many windows, which
 * improves vehicle routing density and reduces deadhead trips.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreedySlotAllocator implements SlotAllocator {

    private final DeliverySlotRepository slotRepository;
    private final AppProperties props;

    @Override
    public Optional<DeliverySlot> allocate(DeliveryZone zone, LocalDate targetDate) {
        List<DeliverySlot> candidates = slotRepository
            .findAvailableByZoneAndDate(zone.getId(), targetDate);

        if (candidates.isEmpty()) {
            log.debug("No candidate slots for zone={} date={}", zone.getId(), targetDate);
            return Optional.empty();
        }

        double overloadThreshold = props.getSlot().getOverloadThreshold();
        int fleetThroughput = fleetThroughput(zone);

        return candidates.stream()
            // Hard constraint 1: slot is not over its own capacity
            .filter(s -> s.getBookedCount() < s.getCapacity())
            // Hard constraint 2: slot fill rate below overload threshold
            .filter(s -> s.fillRate() < overloadThreshold)
            // Hard constraint 3: vehicle fleet can absorb this delivery
            .filter(s -> s.getBookedCount() < fleetThroughput)
            // Soft objective: prefer slots with the highest current fill-rate
            // (pack slots greedily before opening new windows)
            .max(Comparator.comparingDouble(DeliverySlot::fillRate))
            .or(() -> {
                // Fallback: if all slots are above threshold, pick least-loaded
                log.debug("All slots above threshold for zone={}, falling back to least-loaded", zone.getId());
                return candidates.stream()
                    .filter(s -> s.getBookedCount() < s.getCapacity())
                    .min(Comparator.comparingDouble(DeliverySlot::fillRate));
            });
    }

    /**
     * How many orders can the zone fleet handle per slot window?
     * Formula: activeVehicles × (windowDurationMinutes / avgDeliveryMinutes)
     */
    private int fleetThroughput(DeliveryZone zone) {
        if (zone.getFleetCapacity() == null) {
            return zone.getMaxOrdersPerSlot();
        }
        int avgDelivery = zone.getFleetCapacity().getAvgDeliveryMinutes();
        int windowMin  = props.getSlot().getWindowDurationMinutes();
        int vehicles   = zone.getFleetCapacity().getActiveVehicles();

        if (avgDelivery <= 0) return zone.getMaxOrdersPerSlot();

        // Orders per vehicle per window × number of vehicles
        double ordersPerVehicle = (double) windowMin / avgDelivery;
        return Math.max(1, (int) Math.floor(ordersPerVehicle * vehicles));
    }
}
