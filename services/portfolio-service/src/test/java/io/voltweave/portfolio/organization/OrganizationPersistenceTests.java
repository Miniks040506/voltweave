package io.voltweave.portfolio.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class OrganizationPersistenceTests {
    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void flywayCreatedTheVersionedSchema() {
        Integer migrationCount = jdbcClient.sql("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success
                """)
                .query(Integer.class)
                .single();

        assertThat(migrationCount).isEqualTo(1);
    }

    @Test
    void createsOrganizationAndOwnerInOneUseCase() {
        var organization = createOrganization("north-grid", "keycloak-owner");

        var loaded = organizationRepository
                .findByIdForSubject(organization.id(), "keycloak-owner")
                .orElseThrow();
        var owner = memberRepository
                .findActive(organization.id(), "keycloak-owner")
                .orElseThrow();

        assertThat(loaded.id()).isEqualTo(organization.id());
        assertThat(loaded.tenantCode()).isEqualTo("north-grid");
        assertThat(owner.role()).isEqualTo(OrganizationRole.OWNER);
        assertThat(owner.organizationId()).isEqualTo(organization.id());
    }

    @Test
    void deniesLookupForAUserOutsideTheOrganization() {
        var organization = createOrganization("private-grid", "member-a");

        assertThat(organizationRepository
                .findByIdForSubject(organization.id(), "member-b"))
                .isEmpty();
    }

    @Test
    void allowsOneSubjectToBelongToMultipleOrganizations() {
        var first = createOrganization("east-grid", "shared-subject");
        var second = createOrganization("west-grid", "shared-subject");

        assertThat(memberRepository.findActiveBySubject("shared-subject"))
                .extracting(OrganizationMember::organizationId)
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void rejectsDuplicateMembershipInsideOneOrganization() {
        var organization = createOrganization("unique-grid", "same-subject");
        var duplicate = OrganizationMember.active(
                organization.id(), "same-subject", OrganizationRole.MEMBER, Instant.now()
        );

        assertThatThrownBy(() -> memberRepository.insert(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Organization createOrganization(String tenantCode, String subjectId) {
        return organizationService.create(
                new CreateOrganizationCommand(
                        OrganizationType.VPP_OPERATOR,
                        "VoltWeave Energy Limited",
                        "VoltWeave " + tenantCode,
                        tenantCode,
                        "VN",
                        "Asia/Ho_Chi_Minh"
                ),
                subjectId
        );
    }
}
