package io.voltweave.simulator.mqtt;

import java.time.Instant;
import java.util.UUID;

public record DeviceStatus(
        UUID deviceId,
        String status,
        Instant observedAt
) {
}
