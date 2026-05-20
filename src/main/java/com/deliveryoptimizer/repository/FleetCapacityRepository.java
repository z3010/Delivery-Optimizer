package com.deliveryoptimizer.repository;

import com.deliveryoptimizer.model.entity.FleetCapacity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FleetCapacityRepository extends JpaRepository<FleetCapacity, Long> {
    Optional<FleetCapacity> findByZoneId(Long zoneId);
}
