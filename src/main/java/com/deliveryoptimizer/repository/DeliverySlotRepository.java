package com.deliveryoptimizer.repository;

import com.deliveryoptimizer.model.entity.DeliverySlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeliverySlotRepository extends JpaRepository<DeliverySlot, Long> {

    /** Available slots for a zone on a given date, ordered by window start */
    @Query("""
        SELECT s FROM DeliverySlot s
        WHERE s.zone.id = :zoneId
          AND s.slotDate = :date
          AND s.isAvailable = true
          AND s.bookedCount < s.capacity
        ORDER BY s.windowStart ASC
    """)
    List<DeliverySlot> findAvailableByZoneAndDate(
        @Param("zoneId") Long zoneId,
        @Param("date") LocalDate date
    );

    /** Pessimistic write lock for safe concurrent booking */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DeliverySlot s WHERE s.id = :id")
    Optional<DeliverySlot> findByIdForUpdate(@Param("id") Long id);

    /** All slots for a zone across all dates */
    List<DeliverySlot> findByZoneIdOrderBySlotDateAscWindowStartAsc(Long zoneId);

    /** Bulk fetch all slots for a date (used by seeder / daily refresh) */
    List<DeliverySlot> findBySlotDate(LocalDate date);

    /** Count of available slots per zone for today */
    @Query("""
        SELECT s.zone.id, COUNT(s)
        FROM DeliverySlot s
        WHERE s.slotDate = :date AND s.isAvailable = true AND s.bookedCount < s.capacity
        GROUP BY s.zone.id
    """)
    List<Object[]> countAvailableByZoneForDate(@Param("date") LocalDate date);

    boolean existsByZoneIdAndSlotDateAndWindowStart(
        Long zoneId, LocalDate slotDate, java.time.LocalTime windowStart
    );
}
