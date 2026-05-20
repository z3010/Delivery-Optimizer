package com.deliveryoptimizer.repository;

import com.deliveryoptimizer.model.entity.DeliveryZone;
import com.deliveryoptimizer.model.enums.ZoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {

    List<DeliveryZone> findByStatus(ZoneStatus status);

    @Query("SELECT z FROM DeliveryZone z LEFT JOIN FETCH z.fleetCapacity WHERE z.status = :status")
    List<DeliveryZone> findByStatusWithFleet(ZoneStatus status);

    boolean existsByNameAndCity(String name, String city);
}
