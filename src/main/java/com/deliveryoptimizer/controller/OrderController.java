package com.deliveryoptimizer.controller;

import com.deliveryoptimizer.model.dto.BulkSimulateRequest;
import com.deliveryoptimizer.model.dto.CreateOrderRequest;
import com.deliveryoptimizer.model.dto.OrderResponse;
import com.deliveryoptimizer.service.OrderService;
import com.deliveryoptimizer.service.SimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SimulatorService simulatorService;

    /**
     * POST /api/orders
     * Create a single order and allocate a slot.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        OrderResponse response = orderService.createOrder(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /**
     * GET /api/orders/zone/{zoneId}?page=0&size=20
     */
    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<Page<OrderResponse>> getOrdersByZone(
        @PathVariable Long zoneId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getOrdersByZone(zoneId, pageable));
    }

    /**
     * POST /api/orders/simulate/bulk
     * Fires N synthetic orders to stress-test slot allocation.
     */
    @PostMapping("/simulate/bulk")
    public ResponseEntity<Map<String, Object>> bulkSimulate(
        @Valid @RequestBody BulkSimulateRequest req
    ) {
        List<OrderResponse> results = simulatorService.simulateBulk(req);
        long allocated = results.stream()
            .filter(r -> r.slotId() != null)
            .count();
        return ResponseEntity.ok(Map.of(
            "requested", req.orderCount(),
            "created", results.size(),
            "allocated", allocated,
            "failed", results.size() - allocated
        ));
    }
}
