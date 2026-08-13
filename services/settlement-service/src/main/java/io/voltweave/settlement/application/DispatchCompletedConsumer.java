package io.voltweave.settlement.application;

import java.time.Clock;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.dispatch.v1.DispatchCompletedPayloadV1;
import io.voltweave.settlement.persistence.SettlementRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DispatchCompletedConsumer {
    private final ObjectMapper objectMapper;
    private final SettlementApplicationService settlementService;
    private final Clock clock;

    @Autowired
    public DispatchCompletedConsumer(
            ObjectMapper objectMapper,
            SettlementApplicationService settlementService
    ) {
        this(objectMapper, settlementService, Clock.systemUTC());
    }

    DispatchCompletedConsumer(
            ObjectMapper objectMapper,
            SettlementApplicationService settlementService,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.settlementService = settlementService;
        this.clock = clock;
    }

    @KafkaListener(
            topics = EventTopics.DISPATCH_LIFECYCLE_V1,
            groupId = SettlementRepository.DISPATCH_CONSUMER
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            if (!EventTypes.DISPATCH_COMPLETED.equals(event.path("eventType").asString())) {
                return;
            }
            ParsedCompletion parsed = parse(record, event);
            settlementService.calculate(
                    parsed.eventId(), parsed.organizationId(), parsed.payload(), clock.instant()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid dispatch.completed event", exception);
        }
    }

    private ParsedCompletion parse(ConsumerRecord<String, String> record, JsonNode event)
            throws Exception {
        if (event == null || !event.isObject()
                || event.path("eventVersion").asInt() != 1
                || !"dispatch-service".equals(event.path("producer").asString())) {
            throw new IllegalArgumentException("Unsupported dispatch completion contract");
        }
        UUID eventId = UUID.fromString(event.path("eventId").asString());
        UUID organizationId = UUID.fromString(event.path("tenantId").asString());
        var payload = objectMapper.treeToValue(
                event.path("payload"), DispatchCompletedPayloadV1.class
        );
        String dispatchKey = payload.dispatchId().toString();
        if (!dispatchKey.equals(event.path("partitionKey").asString())
                || !dispatchKey.equals(record.key())) {
            throw new IllegalArgumentException("Completion event key must equal dispatchId");
        }
        return new ParsedCompletion(eventId, organizationId, payload);
    }

    private record ParsedCompletion(
            UUID eventId,
            UUID organizationId,
            DispatchCompletedPayloadV1 payload
    ) {
    }
}
