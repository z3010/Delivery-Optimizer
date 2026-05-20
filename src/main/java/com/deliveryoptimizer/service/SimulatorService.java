package com.deliveryoptimizer.service;

import com.deliveryoptimizer.model.dto.BulkSimulateRequest;
import com.deliveryoptimizer.model.dto.CreateOrderRequest;
import com.deliveryoptimizer.model.dto.OrderResponse;
import com.deliveryoptimizer.model.entity.DeliveryZone;
import com.deliveryoptimizer.model.enums.ZoneStatus;
import com.deliveryoptimizer.repository.DeliveryZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorService {

    private static final List<String> NAMES = List.of(
        "Aarav Shah", "Priya Iyer", "Rohan Mehta", "Sneha Pillai", "Arjun Nair",
        "Divya Krishnan", "Vikram Bose", "Ananya Singh", "Karan Gupta", "Meera Joshi"
    );

    private static final List<String> STREETS = List.of(
        "MG Road", "Nehru Nagar", "Gandhi Chowk", "Bandra West", "Koramangala",
        "Indiranagar", "Jubilee Hills", "Anna Nagar", "Connaught Place", "Salt Lake"
    );

    private final OrderService orderService;
    private final DeliveryZoneRepository zoneRepository;
    private final Random random = new Random();

    public List<OrderResponse> simulateBulk(BulkSimulateRequest req) {
        List<DeliveryZone> zones = resolveZones(req);
        if (zones.isEmpty()) {
            throw new IllegalArgumentException("No active zones available for simulation");
        }

        List<OrderResponse> results = new ArrayList<>();
        int delayMs = req.delayMs() != null ? req.delayMs() : 0;

        for (int i = 0; i < req.orderCount(); i++) {
            DeliveryZone zone = Boolean.TRUE.equals(req.randomZones())
                ? zones.get(random.nextInt(zones.size()))
                : zones.get(0);

            CreateOrderRequest orderReq = buildFakeOrder(zone.getId());

            try {
                OrderResponse response = orderService.createOrder(orderReq);
                results.add(response);
            } catch (Exception ex) {
                log.warn("Simulated order {} failed for zone {}: {}", i, zone.getId(), ex.getMessage());
            }

            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            }
        }

        log.info("Bulk simulation complete: requested={} created={}", req.orderCount(), results.size());
        return results;
    }

    private List<DeliveryZone> resolveZones(BulkSimulateRequest req) {
        if (req.zoneId() != null) {
            return zoneRepository.findById(req.zoneId())
                .map(List::of)
                .orElse(List.of());
        }
        return zoneRepository.findByStatus(ZoneStatus.ACTIVE);
    }

    private CreateOrderRequest buildFakeOrder(Long zoneId) {
        String ref = "SIM-" + System.nanoTime() + "-" + random.nextInt(9999);
        String name = NAMES.get(random.nextInt(NAMES.size()));
        String street = STREETS.get(random.nextInt(STREETS.size()));
        String address = (random.nextInt(999) + 1) + " " + street + ", Apt " + (random.nextInt(50) + 1);
        String phone = "9" + String.format("%09d", (long)(random.nextDouble() * 1_000_000_000L));

        return new CreateOrderRequest(
            ref, zoneId, LocalDate.now(), name, phone, address
        );
    }
}
