package io.voltweave.telemetry.processing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.contracts.events.v1.TelemetryQualityV1;
import io.voltweave.contracts.events.v1.TelemetryRawPayloadV1;
import io.voltweave.telemetry.ingress.TelemetryTopic;
import io.voltweave.telemetry.processing.application.exception.TelemetryValidationException;
import io.voltweave.telemetry.processing.application.model.IncomingTelemetry;
import io.voltweave.telemetry.processing.application.model.TelemetryCursor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class TelemetryNormalizer {
    private static final Set<String> DEVICE_TYPES = Set.of(
            "SMART_METER", "SOLAR_INVERTER", "BATTERY", "EV_CHARGER"
    );
    private static final BigDecimal MAX_POWER_KW = BigDecimal.valueOf(999_999_999);

    private final ObjectMapper objectMapper;
    private final Duration staleAfter;
    private final Duration outOfOrderWindow;

    public TelemetryNormalizer(
            ObjectMapper objectMapper,
            @Value("${voltweave.processing.stale-after:5m}") Duration staleAfter,
            @Value("${voltweave.processing.out-of-order-window:2m}") Duration outOfOrderWindow
    ) {
        this.objectMapper = objectMapper;
        this.staleAfter = requirePositive(staleAfter, "stale-after");
        this.outOfOrderWindow = requirePositive(outOfOrderWindow, "out-of-order-window");
    }

    public TelemetryNormalizedPayloadV1 normalize(
            UUID organizationId,
            TelemetryRawPayloadV1 raw,
            Instant receivedAt,
            Optional<TelemetryCursor> cursor
    ) {
        TelemetryTopic topic = TelemetryTopic.parse(raw.mqttTopic());
        if (!organizationId.equals(topic.organizationId())
                || !raw.siteId().equals(topic.siteId())
                || !raw.deviceId().equals(topic.deviceId())) {
            throw invalid("IDENTITY_MISMATCH", "Raw event identities do not match MQTT topic");
        }

        IncomingTelemetry incoming = decode(raw.payloadBase64());
        validate(incoming, raw.deviceId(), receivedAt);
        TelemetryQualityV1 quality = classify(incoming, receivedAt, cursor);

        return new TelemetryNormalizedPayloadV1(
                raw.siteId(),
                raw.deviceId(),
                incoming.sequenceNumber(),
                incoming.observedAt(),
                receivedAt,
                incoming.type(),
                incoming.activePowerKw().setScale(3, RoundingMode.HALF_UP),
                incoming.socPercent() == null ? null
                        : incoming.socPercent().setScale(3, RoundingMode.HALF_UP),
                incoming.online(),
                quality
        );
    }

    private IncomingTelemetry decode(String payloadBase64) {
        try {
            byte[] payload = Base64.getDecoder().decode(payloadBase64);
            return objectMapper.readValue(payload, IncomingTelemetry.class);
        } catch (IllegalArgumentException exception) {
            throw new TelemetryValidationException(
                    "INVALID_BASE64", "Raw telemetry is not valid Base64", exception
            );
        } catch (JacksonException exception) {
            throw new TelemetryValidationException(
                    "INVALID_JSON", "Telemetry payload is not valid JSON", exception
            );
        }
    }

    private static void validate(
            IncomingTelemetry value,
            UUID expectedDeviceId,
            Instant receivedAt
    ) {
        if (value.deviceId() == null || !value.deviceId().equals(expectedDeviceId)) {
            throw invalid("IDENTITY_MISMATCH", "Payload deviceId does not match MQTT topic");
        }
        if (value.sequenceNumber() == null || value.sequenceNumber() <= 0) {
            throw invalid("INVALID_SEQUENCE", "sequenceNumber must be positive");
        }
        if (value.observedAt() == null || value.observedAt().isAfter(receivedAt)) {
            throw invalid("INVALID_TIMESTAMP", "observedAt must not be after receivedAt");
        }
        if (!DEVICE_TYPES.contains(value.type())) {
            throw invalid("INVALID_DEVICE_TYPE", "Unsupported device type");
        }
        if (value.activePowerKw() == null
                || value.activePowerKw().abs().compareTo(MAX_POWER_KW) > 0) {
            throw invalid("INVALID_POWER", "activePowerKw is missing or outside V1 range");
        }
        boolean storesEnergy = "BATTERY".equals(value.type()) || "EV_CHARGER".equals(value.type());
        if (storesEnergy != (value.socPercent() != null)) {
            throw invalid("INVALID_SOC", "SOC is required only for battery and EV charger");
        }
        if (value.socPercent() != null
                && (value.socPercent().signum() < 0
                || value.socPercent().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw invalid("INVALID_SOC", "socPercent must be between 0 and 100");
        }
        if (value.online() == null) {
            throw invalid("INVALID_SCHEMA", "online is required");
        }
    }

    private TelemetryQualityV1 classify(
            IncomingTelemetry value,
            Instant receivedAt,
            Optional<TelemetryCursor> cursor
    ) {
        if (cursor.isPresent() && (value.sequenceNumber() < cursor.get().sequenceNumber()
                || value.observedAt().isBefore(cursor.get().observedAt()))) {
            Duration lateness = Duration.between(value.observedAt(), cursor.get().observedAt());
            if (lateness.compareTo(outOfOrderWindow) > 0) {
                throw invalid("OUT_OF_ORDER_LIMIT", "Telemetry exceeds out-of-order window");
            }
            return TelemetryQualityV1.OUT_OF_ORDER;
        }
        return Duration.between(value.observedAt(), receivedAt).compareTo(staleAfter) > 0
                ? TelemetryQualityV1.STALE : TelemetryQualityV1.VALID;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static TelemetryValidationException invalid(String code, String message) {
        return new TelemetryValidationException(code, message);
    }
}
