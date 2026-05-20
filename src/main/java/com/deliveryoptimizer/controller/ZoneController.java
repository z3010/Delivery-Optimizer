package com.deliveryoptimizer.controller;

import com.deliveryoptimizer.model.dto.ZoneLoadResponse;
import com.deliveryoptimizer.service.ZoneLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneLoadService zoneLoadService;

    /**
     * GET /api/zones
     * All active zones with current load snapshot.
     */
    @GetMapping
    public ResponseEntity<List<ZoneLoadResponse>> getAllZones() {
        return ResponseEntity.ok(zoneLoadService.getAllZoneLoads());
    }

    /**
     * GET /api/zones/{zoneId}/load
     * Real-time load for a single zone.
     */
    @GetMapping("/{zoneId}/load")
    public ResponseEntity<ZoneLoadResponse> getZoneLoad(@PathVariable Long zoneId) {
        return ResponseEntity.ok(zoneLoadService.getZoneLoad(zoneId));
    }
}
