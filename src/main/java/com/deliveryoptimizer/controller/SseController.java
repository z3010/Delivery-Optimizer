package com.deliveryoptimizer.controller;

import com.deliveryoptimizer.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/stream")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterRegistry registry;

    /**
     * GET /api/stream/zones
     *
     * Opens a persistent SSE connection. The client receives:
     *   - event: zone-update   (single zone, triggered by order placement)
     *   - event: zone-snapshot (all zones, sent every 15s as heartbeat)
     *
     * EventSource on the frontend will auto-reconnect on drop.
     */
    @GetMapping(value = "/zones", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamZones() {
        log.info("New SSE subscriber (total={})", registry.size() + 1);
        return registry.register();
    }
}
