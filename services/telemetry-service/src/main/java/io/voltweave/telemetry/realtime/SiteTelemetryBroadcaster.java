package io.voltweave.telemetry.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;

@Component
public class SiteTelemetryBroadcaster {
    private final ConcurrentHashMap<SiteKey, CopyOnWriteArrayList<SseEmitter>> subscribers =
            new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public SiteTelemetryBroadcaster(
            @Value("${voltweave.realtime.stream-timeout:55s}") Duration timeout
    ) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("stream-timeout must be positive");
        }
        timeoutMillis = timeout.toMillis();
    }

    public SseEmitter subscribe(UUID organizationId, UUID siteId) {
        var key = new SiteKey(organizationId, siteId);
        var emitter = new SseEmitter(timeoutMillis);
        subscribers.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(ignored -> remove(key, emitter));
        return emitter;
    }

    public void publish(UUID organizationId, TelemetryNormalizedPayloadV1 telemetry) {
        var key = new SiteKey(organizationId, telemetry.siteId());
        for (var emitter : List.copyOf(subscribers.getOrDefault(
                key, new CopyOnWriteArrayList<>()
        ))) {
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(telemetry.sequenceNumber()))
                        .name("telemetry")
                        .data(telemetry));
            } catch (IOException | IllegalStateException exception) {
                emitter.complete();
                remove(key, emitter);
            }
        }
    }

    int subscriberCount(UUID organizationId, UUID siteId) {
        return subscribers.getOrDefault(
                new SiteKey(organizationId, siteId), new CopyOnWriteArrayList<>()
        ).size();
    }

    private void remove(SiteKey key, SseEmitter emitter) {
        subscribers.computeIfPresent(key, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private record SiteKey(UUID organizationId, UUID siteId) {
    }
}
