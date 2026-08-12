package io.voltweave.telemetry.processing.application.model;

import java.time.Instant;

public record TelemetryCursor(long sequenceNumber, Instant observedAt) {
}
