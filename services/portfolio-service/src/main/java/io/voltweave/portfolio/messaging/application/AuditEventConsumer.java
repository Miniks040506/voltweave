package io.voltweave.portfolio.messaging.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.audit.v1.AuditRecordedPayloadV1;
import io.voltweave.portfolio.audit.domain.entities.AuditEntry;
import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditActorType;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;
import io.voltweave.portfolio.audit.persistence.AuditEntryRepository;
import io.voltweave.portfolio.messaging.persistence.InboxRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "voltweave.messaging", name = "enabled", havingValue = "true"
)
public class AuditEventConsumer {
    static final String CONSUMER_NAME = "portfolio-audit-v1";

    private final ObjectMapper objectMapper;
    private final InboxRepository inboxRepository;
    private final AuditEntryRepository auditRepository;

    public AuditEventConsumer(
            ObjectMapper objectMapper,
            InboxRepository inboxRepository,
            AuditEntryRepository auditRepository
    ) {
        this.objectMapper = objectMapper;
        this.inboxRepository = inboxRepository;
        this.auditRepository = auditRepository;
    }

    @KafkaListener(topics = EventTopics.AUDIT_V1, groupId = CONSUMER_NAME)
    @Transactional
    public void consume(String value) {
        try {
            var event = objectMapper.readTree(value);
            var eventId = UUID.fromString(event.path("eventId").asString());
            var eventType = event.path("eventType").asString();
            if (event.path("eventVersion").asInt() != 1
                    || !EventTypes.AUDIT_RECORDED.equals(eventType)) {
                throw new IllegalArgumentException("Unsupported audit event contract");
            }
            if (!inboxRepository.recordIfNew(
                    CONSUMER_NAME, eventId, eventType, Instant.now()
            )) {
                return;
            }

            var payload = objectMapper.treeToValue(
                    event.path("payload"), AuditRecordedPayloadV1.class
            );
            auditRepository.insertIfAbsent(new AuditEntry(
                    payload.auditEntryId(),
                    UUID.fromString(event.path("tenantId").asString()),
                    AuditActorType.valueOf(payload.actorType()),
                    payload.actorId(),
                    AuditAction.valueOf(payload.action()),
                    AuditResourceType.valueOf(payload.resourceType()),
                    payload.resourceId(),
                    Instant.parse(event.path("occurredAt").asString()),
                    UUID.fromString(event.path("correlationId").asString()),
                    null,
                    null
            ));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid audit event JSON", exception);
        }
    }
}
