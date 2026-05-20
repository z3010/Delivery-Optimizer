package com.deliveryoptimizer.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Redis cache layer for fast slot state management.
 *
 * Key schema:
 *   slot:available:{slotId}          → remaining capacity (integer string)
 *   slot:booked:{slotId}             → booked count (integer string)
 *   zone:load:{zoneId}               → active order count (integer string)
 *   zone:available_slots:{zoneId}:{date} → count of available slots (integer string)
 *
 * All keys carry a TTL to prevent stale data surviving a crash/restart.
 * The DB is the source of truth — Redis is the fast read/write layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSlotCache {

    private static final Duration SLOT_TTL  = Duration.ofHours(24);
    private static final Duration ZONE_TTL  = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    // ------------------------------------------------------------------ Keys

    private String slotAvailableKey(Long slotId) {
        return "slot:available:" + slotId;
    }

    private String slotBookedKey(Long slotId) {
        return "slot:booked:" + slotId;
    }

    private String zoneLoadKey(Long zoneId) {
        return "zone:load:" + zoneId;
    }

    private String zoneAvailableSlotsKey(Long zoneId, LocalDate date) {
        return "zone:available_slots:" + zoneId + ":" + date;
    }

    // -------------------------------------------------------- Slot capacity

    /** Seed slot counters from DB values (called on startup and slot refresh). */
    public void initSlot(Long slotId, int capacity, int booked) {
        redis.opsForValue().set(slotAvailableKey(slotId),
            String.valueOf(capacity - booked), SLOT_TTL);
        redis.opsForValue().set(slotBookedKey(slotId),
            String.valueOf(booked), SLOT_TTL);
    }

    /**
     * Atomic decrement of remaining capacity.
     * Returns the new remaining value, or -1 if the key doesn't exist
     * (caller should fall back to DB and re-seed).
     */
    public long decrementAvailable(Long slotId) {
        String key = slotAvailableKey(slotId);
        Long result = redis.opsForValue().decrement(key);
        if (result == null) return -1L;
        // Refresh TTL on write
        redis.expire(key, SLOT_TTL);
        redis.opsForValue().increment(slotBookedKey(slotId));
        redis.expire(slotBookedKey(slotId), SLOT_TTL);
        return result;
    }

    /** Undo a booking (e.g. order cancelled or slot reallocation). */
    public void incrementAvailable(Long slotId) {
        redis.opsForValue().increment(slotAvailableKey(slotId));
        redis.expire(slotAvailableKey(slotId), SLOT_TTL);

        Long booked = redis.opsForValue().decrement(slotBookedKey(slotId));
        if (booked != null && booked < 0) {
            redis.opsForValue().set(slotBookedKey(slotId), "0");
        }
        redis.expire(slotBookedKey(slotId), SLOT_TTL);
    }

    public Optional<Integer> getAvailable(Long slotId) {
        String val = redis.opsForValue().get(slotAvailableKey(slotId));
        return Optional.ofNullable(val).map(Integer::parseInt);
    }

    public Optional<Integer> getBooked(Long slotId) {
        String val = redis.opsForValue().get(slotBookedKey(slotId));
        return Optional.ofNullable(val).map(Integer::parseInt);
    }

    // ----------------------------------------------------------- Zone load

    /** Set the active order count for a zone (refreshed by scheduler). */
    public void setZoneLoad(Long zoneId, long activeOrders) {
        redis.opsForValue().set(zoneLoadKey(zoneId),
            String.valueOf(activeOrders), ZONE_TTL);
    }

    public long incrementZoneLoad(Long zoneId) {
        Long result = redis.opsForValue().increment(zoneLoadKey(zoneId));
        redis.expire(zoneLoadKey(zoneId), ZONE_TTL);
        return result != null ? result : 0L;
    }

    public long decrementZoneLoad(Long zoneId) {
        Long result = redis.opsForValue().decrement(zoneLoadKey(zoneId));
        if (result != null && result < 0) {
            redis.opsForValue().set(zoneLoadKey(zoneId), "0");
            return 0L;
        }
        redis.expire(zoneLoadKey(zoneId), ZONE_TTL);
        return result != null ? result : 0L;
    }

    public Optional<Long> getZoneLoad(Long zoneId) {
        String val = redis.opsForValue().get(zoneLoadKey(zoneId));
        return Optional.ofNullable(val).map(Long::parseLong);
    }

    // ------------------------------------------------------- Available slots count

    public void setZoneAvailableSlots(Long zoneId, LocalDate date, long count) {
        redis.opsForValue().set(
            zoneAvailableSlotsKey(zoneId, date), String.valueOf(count), SLOT_TTL);
    }

    public Optional<Long> getZoneAvailableSlots(Long zoneId, LocalDate date) {
        String val = redis.opsForValue().get(zoneAvailableSlotsKey(zoneId, date));
        return Optional.ofNullable(val).map(Long::parseLong);
    }

    // ------------------------------------------------------------ Eviction

    public void evictSlot(Long slotId) {
        redis.delete(slotAvailableKey(slotId));
        redis.delete(slotBookedKey(slotId));
    }

    public void evictZone(Long zoneId) {
        redis.delete(zoneLoadKey(zoneId));
    }
}
