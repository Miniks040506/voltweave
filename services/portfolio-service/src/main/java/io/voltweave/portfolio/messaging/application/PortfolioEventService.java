package io.voltweave.portfolio.messaging.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.portfolio.v1.PortfolioLifecyclePayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;

@Service
public class PortfolioEventService {
    private static final String PRODUCER = "portfolio-service";

    private final OutboxService outboxService;

    public PortfolioEventService(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    public void record(
            UUID tenantId,
            String eventType,
            UUID partitionKey,
            PortfolioLifecyclePayloadV1 payload,
            UUID correlationId
    ) {
        outboxService.enqueue(EventTopics.PORTFOLIO_LIFECYCLE_V1, EventEnvelopeV1.create(
                eventType, PRODUCER, tenantId, correlationId, null,
                partitionKey.toString(), payload, Instant.now()
        ));
    }
}
