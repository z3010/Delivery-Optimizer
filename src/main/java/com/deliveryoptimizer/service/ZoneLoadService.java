package com.deliveryoptimizer.service;

import com.deliveryoptimizer.cache.RedisSlotCache;
import com.deliveryoptimizer.exception.ZoneNotFoundException;
import com.deliveryoptimizer.model.dto.ZoneLoadResponse;
import com.deliveryoptimizer.model.entity.DeliveryZone;
import com.deliveryoptimizer.model.entity.FleetCapacity;
import com.deliveryoptimizer.repository.DeliverySlotRepository;
import com.deliveryoptimizer.repository.DeliveryZoneRepository;
import com.deliveryoptimizer.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneLoadService {

    private final DeliveryZoneRepository zoneRepository;
    private final OrderRepository orderRepository;
    private final DeliverySlotRepository slotRepository;
    private final RedisSlotCache redisCache;

    @Transactional(readOnly = true)
    public List<ZoneLoadResponse> getAllZoneLoads() {
        return zoneRepository.findAll()
            .stream()
            .map(this::buildLoad)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ZoneLoadResponse getZoneLoad(Long zoneId) {
        DeliveryZone zone = zoneRepository.findById(zoneId)
            .orElseThrow(() -> new ZoneNotFoundException(zoneId));
        return buildLoad(zone);
    }

    /**
     * Periodically refresh Redis zone-load counters from DB.
     * This reconciles any drift between Redis and Postgres
     * (e.g. after a restart or Redis eviction).
     */
    @Scheduled(fixedDelayString = "${app.sse.heartbeat-interval-ms:15000}")
    @Transactional(readOnly = true)
    public void syncZoneLoadToRedis() {
        List<Object[]> rows = orderRepository.countActiveOrdersPerZone();
        for (Object[] row : rows) {
            Long zoneId = ((Number) row[0]).longValue();
            Long count  = ((Number) row[1]).longValue();
            redisCache.setZoneLoad(zoneId, count);
        }
        log.debug("Zone load synced to Redis for {} zones", rows.size());
    }

    // ----------------------------------------------------------------- helper

    private ZoneLoadResponse buildLoad(DeliveryZone zone) {
        // Redis-first for active order count
        long activeOrders = redisCache.getZoneLoad(zone.getId())
            .orElseGet(() -> orderRepository.countActiveOrdersByZone(zone.getId()));

        // Redis-first for available slot count
        long availableSlots = redisCache.getZoneAvailableSlots(zone.getId(), LocalDate.now())
            .orElseGet(() -> countAvailableSlotsFromDb(zone.getId()));

        FleetCapacity fleet = zone.getFleetCapacity();
        int totalVehicles  = fleet != null ? fleet.getTotalVehicles() : 0;
        int activeVehicles = fleet != null ? fleet.getActiveVehicles() : 0;

        // Total capacity = available slots × zone max orders per slot
        long totalCapacity = Math.max(1L,
            availableSlots * zone.getMaxOrdersPerSlot() + activeOrders);
        double loadPercent = Math.min(100.0,
            (double) activeOrders / totalCapacity * 100.0);

        return new ZoneLoadResponse(
            zone.getId(),
            zone.getName(),
            zone.getCity(),
            zone.getStatus(),
            totalVehicles,
            activeVehicles,
            activeOrders,
            availableSlots,
            loadPercent
        );
    }

    private long countAvailableSlotsFromDb(Long zoneId) {
        return slotRepository.findAvailableByZoneAndDate(zoneId, LocalDate.now()).size();
    }
}
