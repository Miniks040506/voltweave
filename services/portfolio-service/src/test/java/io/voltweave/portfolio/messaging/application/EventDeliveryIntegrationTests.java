package io.voltweave.portfolio.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.portfolio.PostgresTestConfiguration;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.domain.enums.OrganizationRole;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;

@SpringBootTest(properties = {
        "voltweave.messaging.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "voltweave.messaging.outbox.poll-delay=1h"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class EventDeliveryIntegrationTests {
    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private AuditEventConsumer auditEventConsumer;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void storesEventsAtomicallyAndIgnoresAuditReplay() {
        var organization = organizationService.create(
                new CreateOrganizationCommand(
                        OrganizationType.VPP_OPERATOR,
                        "Event Delivery Limited",
                        "Event Delivery",
                        "event-delivery",
                        "VN",
                        "Asia/Ho_Chi_Minh"
                ),
                "owner-subject"
        );

        organizationService.addMember(
                organization.id(), "owner-subject", "new-member", OrganizationRole.MEMBER
        );

        assertThat(count("audit_entries")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(2);
        assertThat(jdbcClient.sql("""
                SELECT topic FROM event_outbox ORDER BY topic
                """).query(String.class).list()).containsExactly(
                EventTopics.AUDIT_V1,
                EventTopics.PORTFOLIO_LIFECYCLE_V1
        );

        String auditEvent = jdbcClient.sql("""
                SELECT payload::text FROM event_outbox WHERE topic = :topic
                """)
                .param("topic", EventTopics.AUDIT_V1)
                .query(String.class)
                .single();

        auditEventConsumer.consume(auditEvent);
        auditEventConsumer.consume(auditEvent);

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("audit_entries")).isEqualTo(1);
    }

    private int count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table)
                .query(Integer.class)
                .single();
    }
}
