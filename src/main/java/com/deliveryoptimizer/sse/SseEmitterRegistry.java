package com.deliveryoptimizer.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all active SseEmitter connections.
 * Each client gets a unique ID. Emitters are removed on completion or error.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    // clientId → emitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register() {
        String clientId = UUID.randomUUID().toString();
        // 5-minute timeout — client will reconnect on EventSource reconnect
        SseEmitter emitter = new SseEmitter(300_000L);

        emitters.put(clientId, emitter);
        log.debug("SSE client registered: {} (total={})", clientId, emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(clientId);
            log.debug("SSE client disconnected: {} (total={})", clientId, emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(clientId);
            emitter.complete();
            log.debug("SSE client timed out: {}", clientId);
        });
        emitter.onError(ex -> {
            emitters.remove(clientId);
            log.debug("SSE client error: {} — {}", clientId, ex.getMessage());
        });

        return emitter;
    }

    public Map<String, SseEmitter> getAll() {
        return Map.copyOf(emitters);
    }

    public int size() {
        return emitters.size();
    }
}
