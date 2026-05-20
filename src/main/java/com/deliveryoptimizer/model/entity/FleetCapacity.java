package com.deliveryoptimizer.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "fleet_capacity")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false, unique = true)
    private DeliveryZone zone;

    @Column(name = "total_vehicles", nullable = false)
    @Builder.Default
    private Integer totalVehicles = 10;

    @Column(name = "active_vehicles", nullable = false)
    @Builder.Default
    private Integer activeVehicles = 10;

    @Column(name = "avg_delivery_minutes", nullable = false)
    @Builder.Default
    private Integer avgDeliveryMinutes = 25;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Derived: how many concurrent deliveries can the fleet handle */
    @Transient
    public int concurrentCapacity() {
        // vehicles * (60 / avgDeliveryMinutes) gives orders-per-hour
        if (avgDeliveryMinutes <= 0) return activeVehicles;
        return (int) Math.ceil(activeVehicles * (60.0 / avgDeliveryMinutes));
    }
}
