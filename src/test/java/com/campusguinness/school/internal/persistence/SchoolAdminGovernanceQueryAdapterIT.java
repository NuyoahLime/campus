package com.campusguinness.school.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SchoolAdminGovernanceQueryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired SchoolAdminGovernanceQueryAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void governanceListIncludesAllStatusesAndSupportsFilterSearchAndPagination() {
        UUID pending = insertSchool("Stage14 Pending Campus", "PENDING_ENABLE");
        insertSchool("Stage14 Normal Campus", "NORMAL");
        insertSchool("Stage14 Suspended Campus", "SUSPENDED");
        insertSchool("Stage14 Disabled Campus", "DISABLED");

        var all = adapter.findSchools(null, "Stage14", 0, 20);
        assertThat(all.items()).hasSize(4);
        assertThat(all.items()).extracting(item -> item.status())
                .containsExactlyInAnyOrder("PENDING_ENABLE", "NORMAL", "SUSPENDED", "DISABLED");

        var filtered = adapter.findSchools("PENDING_ENABLE", "Pending", 0, 20);
        assertThat(filtered.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(pending));

        var byInternalCode = adapter.findSchools(null, internalCode(pending), 0, 20);
        assertThat(byInternalCode.items()).extracting(item -> item.id()).containsExactly(pending);

        var byUnifiedCode = adapter.findSchools(null, unifiedCode(pending), 0, 20);
        assertThat(byUnifiedCode.items()).extracting(item -> item.id()).containsExactly(pending);

        var firstPage = adapter.findSchools(null, "Stage14", 0, 2);
        var secondPage = adapter.findSchools(null, "Stage14", 1, 2);
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(secondPage.items()).hasSize(2);
        assertThat(firstPage.items()).extracting(item -> item.id())
                .doesNotContainAnyElementsOf(secondPage.items().stream().map(item -> item.id()).toList());
    }

    @Test
    void countUsesOnlyNormalActiveSchoolAdminAccounts() {
        UUID schoolId = insertSchool("Stage14 Count Campus", "PENDING_ENABLE");
        UUID superAdminId = insertUser("stage14-count-super", "NORMAL", "SUPER_ADMIN", null);

        UUID activeAdmin = insertUser("stage14-count-active", "NORMAL", null, null);
        insertMembership(activeAdmin, schoolId, "SCHOOL_ADMIN", "ACTIVE");

        UUID pendingAdmin = insertUser("stage14-count-pending", "PENDING_ACTIVATION", null, null);
        insertMembership(pendingAdmin, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        insertInvitation(pendingAdmin, schoolId, superAdminId, "PENDING", Instant.now().plusSeconds(3600));

        UUID endedAdmin = insertUser("stage14-count-ended", "NORMAL", null, null);
        insertMembership(endedAdmin, schoolId, "SCHOOL_ADMIN", "ENDED");

        UUID teacher = insertUser("stage14-count-teacher", "NORMAL", null, null);
        insertMembership(teacher, schoolId, "TEACHER", "ACTIVE");

        var detail = adapter.findSchool(schoolId).orElseThrow();
        assertThat(detail.normalActiveSchoolAdminCount()).isEqualTo(1);

        UUID secondActiveAdmin = insertUser("stage14-count-active-two", "NORMAL", null, null);
        insertMembership(secondActiveAdmin, schoolId, "SCHOOL_ADMIN", "ACTIVE");

        assertThat(adapter.findSchool(schoolId).orElseThrow().normalActiveSchoolAdminCount()).isEqualTo(2);
        assertThat(adapter.findSchools("PENDING_ENABLE", "Count Campus", 0, 20).items())
                .singleElement()
                .satisfies(item -> assertThat(item.normalActiveSchoolAdminCount()).isEqualTo(2));
    }

    @Test
    void adminAccountsExcludeOtherRolesAndExposeOnlyGovernanceFields() {
        UUID schoolId = insertSchool("Stage14 Admin Campus", "NORMAL");
        Instant lockedUntil = Instant.now().plusSeconds(600);
        UUID admin = insertUser("stage14-admin-account", "LOCKED", null, lockedUntil);
        insertMembership(admin, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        UUID teacher = insertUser("stage14-teacher-account", "NORMAL", null, null);
        insertMembership(teacher, schoolId, "TEACHER", "ACTIVE");

        assertThat(adapter.findSchoolAdmins(schoolId)).singleElement().satisfies(account -> {
            assertThat(account.userId()).isEqualTo(admin);
            assertThat(account.username()).isEqualTo("stage14-admin-account");
            assertThat(account.accountStatus()).isEqualTo("LOCKED");
            assertThat(account.membershipStatus()).isEqualTo("ACTIVE");
            assertThat(account.startedAt()).isNotNull();
            assertThat(account.lockedUntil()).isNotNull();
        });
    }

    @Test
    void invitationsAreSchoolScopedAndExpiredFlagDoesNotMutateState() {
        UUID schoolA = insertSchool("Stage14 Invitation Campus A", "PENDING_ENABLE");
        UUID schoolB = insertSchool("Stage14 Invitation Campus B", "PENDING_ENABLE");
        UUID creator = insertUser("stage14-invitation-creator", "NORMAL", "SUPER_ADMIN", null);
        UUID userA = insertUser("stage14-invitation-a", "PENDING_ACTIVATION", null, null);
        UUID userB = insertUser("stage14-invitation-b", "PENDING_ACTIVATION", null, null);
        UUID expiredInvitation = insertInvitation(
                userA, schoolA, creator, "PENDING", Instant.now().minusSeconds(60)
        );
        UUID otherSchoolInvitation = insertInvitation(
                userB, schoolB, creator, "PENDING", Instant.now().plusSeconds(3600)
        );

        var page = adapter.findInvitations(schoolA, "PENDING", 0, 20);
        assertThat(page.items()).singleElement().satisfies(invitation -> {
            assertThat(invitation.invitationId()).isEqualTo(expiredInvitation);
            assertThat(invitation.username()).isEqualTo("stage14-invitation-a");
            assertThat(invitation.expired()).isTrue();
        });
        assertThat(adapter.findInvitation(schoolA, expiredInvitation)).isPresent();
        assertThat(adapter.findInvitation(schoolA, otherSchoolInvitation)).isEmpty();
        assertThat(adapter.findInvitation(schoolB, expiredInvitation)).isEmpty();
        assertThat(jdbc.queryForObject(
                "select invitation_status from school_admin_invitations where id = ?",
                String.class,
                expiredInvitation
        )).isEqualTo("PENDING");
    }

    private UUID insertSchool(String name, String status) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("""
                insert into schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) values (?, ?, 'USCC', ?, ?, 'UNIVERSITY', 'Zhejiang',
                          'Stage 14 address', 'Stage 14 contact', '13800000014',
                          'stage14@example.com', ?)
                """, id, name, "STAGE14-U-" + suffix, "STAGE14-I-" + suffix, status);
        return id;
    }

    private UUID insertUser(String username, String status, String platformRole, Instant lockedUntil) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users(id, username, password_hash, account_status, platform_role, locked_until)
                values (?, ?, '{noop}password', ?, ?, ?)
                """, id, username, status, platformRole,
                lockedUntil != null ? java.sql.Timestamp.from(lockedUntil) : null);
        return id;
    }

    private void insertMembership(UUID userId, UUID schoolId, String role, String status) {
        jdbc.update("""
                insert into school_memberships(id, user_id, school_id, role_in_school, status)
                values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, schoolId, role, status);
    }

    private UUID insertInvitation(
            UUID userId,
            UUID schoolId,
            UUID createdBy,
            String status,
            Instant expiresAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into school_admin_invitations(
                    id, user_id, school_id, role_in_school, invitation_code_hash,
                    invitation_status, expires_at, created_by
                ) values (?, ?, ?, 'SCHOOL_ADMIN', 'hash-not-exposed', ?, ?, ?)
                """, id, userId, schoolId, status, java.sql.Timestamp.from(expiresAt), createdBy);
        return id;
    }

    private String internalCode(UUID schoolId) {
        return jdbc.queryForObject("select internal_code from schools where id = ?", String.class, schoolId);
    }

    private String unifiedCode(UUID schoolId) {
        return jdbc.queryForObject("select unified_code from schools where id = ?", String.class, schoolId);
    }
}
