package com.deliveryoptimizer.controller;

import com.deliveryoptimizer.model.dto.SlotAvailabilityResponse;
import com.deliveryoptimizer.service.SlotAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotAllocationService slotAllocationService;

    /**
     * GET /api/slots/zone/{zoneId}?date=2025-06-01
     * Returns available delivery slots for a zone on a given date.
     */
    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<SlotAvailabilityResponse>> getAvailableSlots(
        @PathVariable Long zoneId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(slotAllocationService.getAvailableSlots(zoneId, targetDate));
    }

    /**
     * GET /api/slots/{slotId}
     * Get a single slot's current state.
     */
    @GetMapping("/{slotId}")
    public ResponseEntity<SlotAvailabilityResponse> getSlot(@PathVariable Long slotId) {
        return ResponseEntity.ok(slotAllocationService.getSlot(slotId));
    }

    /**
     * GET /api/slots/zone/{zoneId}/all
     * Full slot grid for a zone (all dates, for dashboard rendering).
     */
    @GetMapping("/zone/{zoneId}/all")
    public ResponseEntity<List<SlotAvailabilityResponse>> getAllSlotsForZone(
        @PathVariable Long zoneId
    ) {
        return ResponseEntity.ok(slotAllocationService.getAllSlotsForZone(zoneId));
    }
}
