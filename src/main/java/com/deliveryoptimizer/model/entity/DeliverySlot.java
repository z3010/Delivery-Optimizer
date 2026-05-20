package com.deliveryoptimizer.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
    name = "delivery_slots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_slot",
        columnNames = {"zone_id", "slot_date", "window_start"}
    )
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliverySlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private DeliveryZone zone;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "window_start", nullable = false)
    private LocalTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalTime windowEnd;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "booked_count", nullable = false)
    @Builder.Default
    private Integer bookedCount = 0;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Transient
    public int remainingCapacity() {
        return capacity - bookedCount;
    }

    @Transient
    public double fillRate() {
        return capacity == 0 ? 1.0 : (double) bookedCount / capacity;
    }
}
