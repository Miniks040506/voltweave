package io.voltweave.simulator.command;

import java.time.Instant;
import java.util.UUID;

public record CommandAcknowledgement(
        UUID commandId,
        String status,
        double appliedPowerKw,
        String reason,
        Instant processedAt
) {
}
