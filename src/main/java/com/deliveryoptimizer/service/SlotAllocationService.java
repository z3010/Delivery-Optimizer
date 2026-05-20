package com.deliveryoptimizer.service;

import com.deliveryoptimizer.algorithm.SlotAllocator;
import com.deliveryoptimizer.cache.RedisSlotCache;
import com.deliveryoptimizer.exception.SlotUnavailableException;
import com.deliveryoptimizer.exception.ZoneNotFoundException;
import com.deliveryoptimizer.model.dto.SlotAvailabilityResponse;
import com.deliveryoptimizer.model.entity.DeliverySlot;
import com.deliveryoptimizer.model.entity.DeliveryZone;
import com.deliveryoptimizer.repository.DeliverySlotRepository;
import com.deliveryoptimizer.repository.DeliveryZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotAllocationService {

    private final SlotAllocator slotAllocator;
    private final DeliverySlotRepository slotRepository;
    private final DeliveryZoneRepository zoneRepository;
    private final RedisSlotCache redisCache;

    /**
     * Allocate a slot for a zone + date.
     * Uses pessimistic locking on the chosen slot row to prevent double-booking.
     */
    @Transactional
    public DeliverySlot allocateSlot(Long zoneId, LocalDate date) {
        DeliveryZone zone = zoneRepository.findById(zoneId)
            .orElseThrow(() -> new ZoneNotFoundException(zoneId));

        // Algorithm picks the best candidate from available slots
        DeliverySlot chosen = slotAllocator.allocate(zone, date)
            .orElseThrow(() -> new SlotUnavailableException(zoneId));

        // Acquire row-level lock and re-check under lock
        DeliverySlot locked = slotRepository.findByIdForUpdate(chosen.getId())
            .orElseThrow(() -> new SlotUnavailableException("Slot disappeared: " + chosen.getId()));

        if (locked.getBookedCount() >= locked.getCapacity()) {
            throw new SlotUnavailableException("Slot " + locked.getId() + " just filled up");
        }

        locked.setBookedCount(locked.getBookedCount() + 1);
        if (locked.getBookedCount() >= locked.getCapacity()) {
            locked.setIsAvailable(false);
        }

        DeliverySlot saved = slotRepository.save(locked);

        // Sync Redis counter
        long remaining = redisCache.decrementAvailable(saved.getId());
        if (remaining < 0) {
            // Key was not in Redis — re-seed it
            redisCache.initSlot(saved.getId(), saved.getCapacity(), saved.getBookedCount());
        }

        log.debug("Slot {} allocated for zone={} date={}, remaining={}",
            saved.getId(), zoneId, date, saved.remainingCapacity());
        return saved;
    }

    // ----------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getAvailableSlots(Long zoneId, LocalDate date) {
        zoneRepository.findById(zoneId).orElseThrow(() -> new ZoneNotFoundException(zoneId));
        return slotRepository.findAvailableByZoneAndDate(zoneId, date)
            .stream()
            .map(s -> toResponse(s, zoneId, null))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SlotAvailabilityResponse getSlot(Long slotId) {
        DeliverySlot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new RuntimeException("Slot not found: " + slotId));

        // Check Redis for fresher booked count
        int booked = redisCache.getBooked(slotId)
            .orElse(slot.getBookedCount());

        return toResponse(slot, slot.getZone().getId(), booked);
    }

    @Transactional(readOnly = true)
    public List<SlotAvailabilityResponse> getAllSlotsForZone(Long zoneId) {
        return slotRepository.findByZoneIdOrderBySlotDateAscWindowStartAsc(zoneId)
            .stream()
            .map(s -> toResponse(s, zoneId, null))
            .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------- helper

    private SlotAvailabilityResponse toResponse(DeliverySlot s, Long zoneId, Integer redisBooked) {
        int booked = redisBooked != null ? redisBooked : s.getBookedCount();
        int remaining = Math.max(0, s.getCapacity() - booked);
        return new SlotAvailabilityResponse(
            s.getId(),
            zoneId,
            s.getZone() != null ? s.getZone().getName() : null,
            s.getSlotDate(),
            s.getWindowStart(),
            s.getWindowEnd(),
            s.getCapacity(),
            booked,
            remaining,
            s.getCapacity() > 0 ? (double) booked / s.getCapacity() : 1.0,
            remaining > 0 && s.getIsAvailable()
        );
    }
}
