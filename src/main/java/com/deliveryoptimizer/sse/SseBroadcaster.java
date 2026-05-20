package com.deliveryoptimizer.sse;

import com.deliveryoptimizer.model.dto.ZoneLoadResponse;
import com.deliveryoptimizer.service.ZoneLoadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseBroadcaster {

    private final SseEmitterRegistry registry;
    private final ZoneLoadService zoneLoadService;
    private final ObjectMapper objectMapper;

    /**
     * Push a targeted update for one zone (called immediately after an order is placed).
     */
    public void broadcastZoneUpdate(Long zoneId) {
        if (registry.size() == 0) return;
        try {
            ZoneLoadResponse load = zoneLoadService.getZoneLoad(zoneId);
            sendToAll("zone-update", load);
        } catch (Exception ex) {
            log.warn("Failed to build zone-update SSE event for zone={}: {}", zoneId, ex.getMessage());
        }
    }

    /**
     * Heartbeat: push full zone snapshot to all clients every 15 seconds.
     * This handles:
     *   - clients that connected just after the last targeted event
     *   - any reconciliation drift between Redis and DB
     */
    @Scheduled(fixedDelayString = "${app.sse.heartbeat-interval-ms:15000}")
    public void broadcastAllZones() {
        if (registry.size() == 0) return;
        try {
            List<ZoneLoadResponse> loads = zoneLoadService.getAllZoneLoads();
            sendToAll("zone-snapshot", loads);
        } catch (Exception ex) {
            log.warn("Heartbeat SSE broadcast failed: {}", ex.getMessage());
        }
    }

    // ----------------------------------------------------------------- internal

    private void sendToAll(String eventName, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.error("SSE serialization error: {}", ex.getMessage());
            return;
        }

        registry.getAll().forEach((clientId, emitter) -> {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(eventName)
                        .data(json)
                );
            } catch (Exception ex) {
                // Emitter is dead — it will remove itself via onError callback
                log.debug("Failed to send SSE to client {}: {}", clientId, ex.getMessage());
            }
        });
    }
}
