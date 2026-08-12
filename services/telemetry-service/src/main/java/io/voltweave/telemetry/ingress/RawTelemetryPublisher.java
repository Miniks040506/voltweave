package io.voltweave.telemetry.ingress;

import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.contracts.events.v1.TelemetryRawPayloadV1;
import tools.jackson.databind.ObjectMapper;

@Component
class RawTelemetryPublisher {
    private static final int KAFKA_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    RawTelemetryPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this(kafkaTemplate, objectMapper, Clock.systemUTC());
    }

    RawTelemetryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    void publish(String mqttTopic, MqttMessage message) {
        TelemetryTopic topic = TelemetryTopic.parse(mqttTopic);
        var payload = new TelemetryRawPayloadV1(
                topic.siteId(),
                topic.deviceId(),
                mqttTopic,
                message.getQos(),
                message.isRetained(),
                Base64.getEncoder().encodeToString(message.getPayload())
        );
        var envelope = EventEnvelopeV1.create(
                EventTypes.TELEMETRY_RAW_RECEIVED,
                "telemetry-service",
                topic.organizationId(),
                UUID.randomUUID(),
                null,
                topic.deviceId().toString(),
                payload,
                clock.instant()
        );

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(EventTopics.TELEMETRY_RAW_V1, envelope.partitionKey(), json)
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Kafka did not persist raw telemetry", exception);
        }
    }
}
