package io.voltweave.telemetry.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import io.voltweave.contracts.events.EventTopics;
import tools.jackson.databind.json.JsonMapper;

class RawTelemetryPublisherTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );
    private static final UUID DEVICE_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
    );
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-12T12:00:00Z");
    private static final String MQTT_TOPIC = "voltweave/%s/%s/%s/telemetry".formatted(
            ORGANIZATION_ID, SITE_ID, DEVICE_ID
    );

    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final RawTelemetryPublisher publisher = new RawTelemetryPublisher(
            kafkaTemplate, objectMapper, Clock.fixed(RECEIVED_AT, ZoneOffset.UTC)
    );

    @Test
    void publishesAKeyedVersionedEnvelopeToTheRawTopic() throws Exception {
        var sendResult = new SendResult<String, String>(
                new ProducerRecord<>(EventTopics.TELEMETRY_RAW_V1, DEVICE_ID.toString(), "{}"),
                null
        );
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        byte[] raw = "{\"sequenceNumber\":7}".getBytes();
        var mqttMessage = new MqttMessage(raw);
        mqttMessage.setQos(1);

        publisher.publish(MQTT_TOPIC, mqttMessage);

        var json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq(EventTopics.TELEMETRY_RAW_V1),
                org.mockito.ArgumentMatchers.eq(DEVICE_ID.toString()),
                json.capture()
        );
        var event = objectMapper.readTree(json.getValue());
        assertThat(event.path("eventType").asString()).isEqualTo("TelemetryRawReceived");
        assertThat(event.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.path("occurredAt").asString()).isEqualTo(RECEIVED_AT.toString());
        assertThat(event.path("tenantId").asString()).isEqualTo(ORGANIZATION_ID.toString());
        assertThat(event.path("partitionKey").asString()).isEqualTo(DEVICE_ID.toString());
        assertThat(event.path("payload").path("siteId").asString())
                .isEqualTo(SITE_ID.toString());
        assertThat(event.path("payload").path("payloadBase64").asString())
                .isEqualTo(Base64.getEncoder().encodeToString(raw));
    }

    @Test
    void failsTheMqttCallbackWhenKafkaDoesNotPersist() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));

        assertThatThrownBy(() -> publisher.publish(MQTT_TOPIC, new MqttMessage("{}".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka did not persist raw telemetry");
    }
}
