package io.voltweave.portfolio.messaging.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.portfolio.messaging.persistence.OutboxRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {
    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, EventEnvelopeV1<?> event) {
        try {
            repository.insert(
                    event.eventId(), topic, event.partitionKey(),
                    objectMapper.writeValueAsString(event), event.occurredAt()
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize event " + event.eventId(), exception);
        }
    }
}
