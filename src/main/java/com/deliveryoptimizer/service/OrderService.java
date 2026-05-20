package com.deliveryoptimizer.service;

import com.deliveryoptimizer.cache.RedisSlotCache;
import com.deliveryoptimizer.exception.ZoneNotFoundException;
import com.deliveryoptimizer.model.dto.CreateOrderRequest;
import com.deliveryoptimizer.model.dto.OrderResponse;
import com.deliveryoptimizer.model.entity.DeliverySlot;
import com.deliveryoptimizer.model.entity.DeliveryZone;
import com.deliveryoptimizer.model.entity.Order;
import com.deliveryoptimizer.model.enums.OrderStatus;
import com.deliveryoptimizer.repository.DeliveryZoneRepository;
import com.deliveryoptimizer.repository.OrderRepository;
import com.deliveryoptimizer.sse.SseBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final DeliveryZoneRepository zoneRepository;
    private final SlotAllocationService slotAllocationService;
    private final ZoneLoadService zoneLoadService;
    private final RedisSlotCache redisCache;
    private final SseBroadcaster sseBroadcaster;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        if (orderRepository.existsByExternalRef(req.externalRef())) {
            // Idempotent: return existing order
            return orderRepository.findByExternalRef(req.externalRef())
                .map(this::toResponse)
                .orElseThrow();
        }

        DeliveryZone zone = zoneRepository.findById(req.zoneId())
            .orElseThrow(() -> new ZoneNotFoundException(req.zoneId()));

        LocalDate targetDate = req.preferredDate() != null
            ? req.preferredDate() : LocalDate.now();

        // Attempt slot allocation (may throw SlotUnavailableException)
        DeliverySlot slot = slotAllocationService.allocateSlot(zone.getId(), targetDate);

        Order order = Order.builder()
            .externalRef(req.externalRef())
            .zone(zone)
            .slot(slot)
            .customerName(req.customerName())
            .customerPhone(req.customerPhone())
            .deliveryAddress(req.deliveryAddress())
            .status(OrderStatus.ALLOCATED)
            .assignedAt(Instant.now())
            .build();

        Order saved = orderRepository.save(order);

        // Update Redis zone load counter
        redisCache.incrementZoneLoad(zone.getId());

        // Push SSE event to all connected dashboard clients
        sseBroadcaster.broadcastZoneUpdate(zone.getId());

        log.info("Order created: id={} externalRef={} zone={} slot={}",
            saved.getId(), saved.getExternalRef(), zone.getId(), slot.getId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return orderRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByZone(Long zoneId, Pageable pageable) {
        zoneRepository.findById(zoneId).orElseThrow(() -> new ZoneNotFoundException(zoneId));
        return orderRepository.findByZoneIdOrderByPlacedAtDesc(zoneId, pageable)
            .map(this::toResponse);
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(
            o.getId(),
            o.getExternalRef(),
            o.getZone().getId(),
            o.getZone().getName(),
            o.getSlot() != null ? o.getSlot().getId() : null,
            o.getSlot() != null ? o.getSlot().getSlotDate() : null,
            o.getSlot() != null ? o.getSlot().getWindowStart() : null,
            o.getSlot() != null ? o.getSlot().getWindowEnd() : null,
            o.getCustomerName(),
            o.getDeliveryAddress(),
            o.getStatus(),
            o.getPlacedAt(),
            o.getAssignedAt()
        );
    }
}
