package com.deliveryoptimizer.repository;

import com.deliveryoptimizer.model.entity.Order;
import com.deliveryoptimizer.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByExternalRef(String externalRef);

    List<Order> findByZoneIdAndStatus(Long zoneId, OrderStatus status);

    Page<Order> findByZoneIdOrderByPlacedAtDesc(Long zoneId, Pageable pageable);

    @Query("""
        SELECT o.zone.id, COUNT(o)
        FROM Order o
        WHERE o.status IN ('PENDING','ALLOCATED','IN_TRANSIT')
        GROUP BY o.zone.id
    """)
    List<Object[]> countActiveOrdersPerZone();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.zone.id = :zoneId AND o.status IN ('PENDING','ALLOCATED','IN_TRANSIT')")
    long countActiveOrdersByZone(@Param("zoneId") Long zoneId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.zone.id = :zoneId AND o.slot.id = :slotId")
    long countByZoneIdAndSlotId(@Param("zoneId") Long zoneId, @Param("slotId") Long slotId);

    boolean existsByExternalRef(String externalRef);
}
