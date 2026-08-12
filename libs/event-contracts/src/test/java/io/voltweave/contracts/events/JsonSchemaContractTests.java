package io.voltweave.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.voltweave.contracts.events.portfolio.v1.PortfolioChangeTypeV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioLifecyclePayloadV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioResourceTypeV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;

class JsonSchemaContractTests {
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void envelopeSchemaMatchesRequiredRecordFields() throws Exception {
        JsonNode schema = schema("event-envelope.schema.json");

        assertThat(schema.path("$schema").asString())
                .isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.path("properties").path("eventVersion").path("const").asInt())
                .isEqualTo(1);
        assertThat(requiredFields(schema)).containsExactlyInAnyOrder(
                "eventId", "eventType", "eventVersion", "occurredAt", "producer",
                "tenantId", "correlationId", "partitionKey", "payload"
        );
    }

    @Test
    void payloadSchemasMatchRequiredRecordFields() throws Exception {
        assertThat(requiredFields(schema("portfolio-lifecycle.schema.json")))
                .containsExactlyInAnyOrder("resourceId", "resourceType", "changeType");
        assertThat(requiredFields(schema("audit-recorded.schema.json")))
                .containsExactlyInAnyOrder(
                        "auditEntryId", "actorType", "actorId", "action",
                        "resourceType", "resourceId"
                );
    }

    @Test
    void serializesEnvelopeUsingSchemaPropertyNames() throws Exception {
        UUID id = UUID.randomUUID();
        var payload = new PortfolioLifecyclePayloadV1(
                id, PortfolioResourceTypeV1.SITE_PREFERENCE,
                PortfolioChangeTypeV1.UPDATED, null
        );
        var envelope = EventEnvelopeV1.create(
                EventTypes.SITE_PREFERENCE_UPDATED, "portfolio-service", id, id,
                null, id.toString(), payload, Instant.parse("2026-08-12T03:00:00Z")
        );

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsBytes(envelope));

        assertThat(json.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.path("payload").path("resourceType").asString())
                .isEqualTo("SITE_PREFERENCE");
    }

    private JsonNode schema(String name) throws Exception {
        String path = "schemas/events/v1/" + name;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return jsonMapper.readTree(input);
        }
    }

    private static Set<String> requiredFields(JsonNode schema) {
        return StreamSupport.stream(schema.path("required").spliterator(), false)
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
    }
}
