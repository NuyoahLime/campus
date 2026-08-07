package com.campusguinness.identity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import com.campusguinness.identity.internal.domain.SchoolMembershipId;
import com.campusguinness.identity.internal.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("School membership persistence")
class SchoolMembershipPersistenceIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private AuthenticationMembershipQuery authenticationMemberships;

    private final String runPrefix = "phase3-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID userId;
    private UUID otherUserId;
    private UUID schoolId;
    private UUID otherSchoolId;
    private Instant startedAt;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        startedAt = Instant.parse("2026-08-06T01:00:00Z");

        insertUser(userId, username("user"), "NORMAL");
        insertUser(otherUserId, username("other"), "NORMAL");
        insertSchool(schoolId, "school");
        insertSchool(otherSchoolId, "other-school");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?,?)", userId, otherUserId);
        jdbc.update("DELETE FROM schools WHERE id IN (?,?)", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", userId, otherUserId);
    }

    @Test
    @DisplayName("UserRepository saves and restores ACTIVE STUDENT membership")
    void userRepositorySavesAndRestoresMembership() {
        var user = users.findByIdForUpdate(new UserId(userId)).orElseThrow();
        user.grantStudentMembership(new SchoolMembershipId(UUID.randomUUID()), schoolId, startedAt);

        users.save(user);

        var restored = users.findById(new UserId(userId)).orElseThrow();
        assertThat(restored.activeMemberships()).hasSize(1);
        assertThat(restored.activeMembershipFor(schoolId)).isPresent();
        assertThat(countMemberships(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("UserRepository updates ended membership without deleting history")
    void userRepositoryUpdatesEndedMembership() {
        var user = users.findByIdForUpdate(new UserId(userId)).orElseThrow();
        user.grantStudentMembership(new SchoolMembershipId(UUID.randomUUID()), schoolId, startedAt);
        users.save(user);

        var toEnd = users.findByIdForUpdate(new UserId(userId)).orElseThrow();
        toEnd.endMembership(schoolId, startedAt.plusSeconds(60));
        users.save(toEnd);

        var restored = users.findById(new UserId(userId)).orElseThrow();
        assertThat(restored.activeMemberships()).isEmpty();
        assertThat(restored.membershipHistoryFor(schoolId)).hasSize(1);
        assertThat(statusFor(userId, schoolId)).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("same user and school can keep multiple ENDED history rows")
    void allowsMultipleEndedHistoryRows() {
        insertMembership(UUID.randomUUID(), userId, schoolId, "STUDENT", "ENDED", startedAt, startedAt.plusSeconds(1));
        insertMembership(UUID.randomUUID(), userId, schoolId, "SCHOOL_ADMIN", "ENDED",
                startedAt.plusSeconds(2), startedAt.plusSeconds(3));

        assertThat(countMemberships(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("same user and school cannot have two ACTIVE memberships")
    void rejectsDuplicateActiveMemberships() {
        insertMembership(UUID.randomUUID(), userId, schoolId, "STUDENT", "ACTIVE", startedAt, null);

        assertThatThrownBy(() -> insertMembership(UUID.randomUUID(), userId, schoolId, "SCHOOL_ADMIN",
                "ACTIVE", startedAt.plusSeconds(1), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("different schools can be ACTIVE at the same time")
    void allowsActiveMembershipsForDifferentSchools() {
        insertMembership(UUID.randomUUID(), userId, schoolId, "STUDENT", "ACTIVE", startedAt, null);
        insertMembership(UUID.randomUUID(), userId, otherSchoolId, "SCHOOL_ADMIN", "ACTIVE", startedAt, null);

        assertThat(countMemberships(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("TEACHER history can be read and active TEACHER is left for principal filtering")
    void teacherHistoryCanBeReadAndPrincipalFiltersAuthority() {
        insertMembership(UUID.randomUUID(), userId, schoolId, "TEACHER", "ENDED",
                startedAt, startedAt.plusSeconds(1));
        UUID activeTeacherSchool = UUID.randomUUID();
        insertSchool(activeTeacherSchool, "legacy-teacher-school");
        insertMembership(UUID.randomUUID(), userId, activeTeacherSchool, "TEACHER", "ACTIVE",
                startedAt.plusSeconds(2), null);
        insertMembership(UUID.randomUUID(), userId, otherSchoolId, "SCHOOL_ADMIN", "ACTIVE",
                startedAt.plusSeconds(2), null);

        var restored = users.findById(new UserId(userId)).orElseThrow();
        var memberships = authenticationMemberships.findActiveByUserId(userId);

        assertThat(restored.memberships()).hasSize(3);
        assertThat(restored.memberships()).anyMatch(m -> m.roleInSchool().equals("TEACHER"));
        assertThat(memberships).extracting("roleInSchool").containsExactlyInAnyOrder("TEACHER", "SCHOOL_ADMIN");
    }

    @Test
    @DisplayName("database rejects invalid role, invalid status, and missing foreign keys")
    void databaseRejectsInvalidRows() {
        assertThatThrownBy(() -> insertMembership(UUID.randomUUID(), userId, schoolId, "SUPER_ADMIN",
                "ACTIVE", startedAt, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertMembership(UUID.randomUUID(), userId, schoolId, "STUDENT",
                "PENDING", startedAt, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertMembership(UUID.randomUUID(), UUID.randomUUID(), schoolId,
                "STUDENT", "ACTIVE", startedAt, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertMembership(UUID.randomUUID(), userId, UUID.randomUUID(),
                "STUDENT", "ACTIVE", startedAt, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(UUID id, String username, String status) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                id, username, "hash", status);
    }

    private void insertSchool(UUID id, String label) {
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-" + label, "USCC", runPrefix + "-c-" + label,
                runPrefix + "-i-" + label.substring(0, Math.min(6, label.length())), "PRIMARY", "Beijing", "Address",
                "Contact", "13800000000", "phase3@example.com", "NORMAL");
    }

    private void insertMembership(
            UUID membershipId,
            UUID userId,
            UUID schoolId,
            String role,
            String status,
            Instant startedAt,
            Instant endedAt
    ) {
        jdbc.update("""
                INSERT INTO school_memberships(
                    id, user_id, school_id, role_in_school, status, started_at, ended_at
                ) VALUES (?,?,?,?,?,?,?)
                """,
                membershipId, userId, schoolId, role, status, Timestamp.from(startedAt),
                endedAt != null ? Timestamp.from(endedAt) : null);
    }

    private int countMemberships(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM school_memberships WHERE user_id = ?",
                Integer.class, id);
    }

    private String statusFor(UUID userId, UUID schoolId) {
        return jdbc.queryForObject(
                "SELECT status FROM school_memberships WHERE user_id = ? AND school_id = ?",
                String.class,
                userId,
                schoolId
        );
    }

    private String username(String label) {
        return runPrefix + "-" + label;
    }
}
