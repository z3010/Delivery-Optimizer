package com.deliveryoptimizer.model.entity;

import com.deliveryoptimizer.model.enums.ZoneStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "delivery_zones")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ZoneStatus status = ZoneStatus.ACTIVE;

    @Column(name = "max_orders_per_slot", nullable = false)
    @Builder.Default
    private Integer maxOrdersPerSlot = 20;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToOne(mappedBy = "zone", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private FleetCapacity fleetCapacity;

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DeliverySlot> slots = new ArrayList<>();
}
