package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@AutoConfigureMockMvc
@TestPropertySource(properties = "campus-guinness.security.cors.allowed-origins=http://localhost:5173")
abstract class ActivityResultReadTestSupport extends PostgreSqlIntegrationTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    protected final String prefix = "result-read-" + UUID.randomUUID();
    protected UUID schoolA;
    protected UUID schoolB;
    protected UUID adminA;
    protected UUID adminB;
    protected UUID studentA;
    protected UUID studentB;
    protected UUID superAdmin;
    protected UUID adminAMembership;
    protected UUID adminBMembership;
    protected UUID studentAMembership;
    protected UUID studentBMembership;

    @BeforeEach
    void setUpReadFixture() {
        schoolA = insertSchool("a");
        schoolB = insertSchool("b");
        adminA = insertUser("admin-a", null);
        adminB = insertUser("admin-b", null);
        studentA = insertUser("student-a", null);
        studentB = insertUser("student-b", null);
        superAdmin = insertUser("super", "SUPER_ADMIN");
        adminAMembership = insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        adminBMembership = insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        studentAMembership = insertMembership(studentA, schoolA, "STUDENT");
        studentBMembership = insertMembership(studentB, schoolB, "STUDENT");
    }

    @AfterEach
    void tearDownReadFixture() {
        jdbc.update("DELETE FROM result_review_records WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("UPDATE activity_results SET current_candidate_version_id = NULL, "
                + "current_internal_version_id = NULL, current_public_version_id = NULL "
                + "WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM result_versions WHERE result_id IN "
                + "(SELECT id FROM activity_results WHERE school_id IN (?, ?))", schoolA, schoolB);
        jdbc.update("DELETE FROM activity_results WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM activities WHERE school_id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?, ?, ?, ?)",
                adminA, adminB, studentA, studentB);
        jdbc.update("DELETE FROM schools WHERE id IN (?, ?)", schoolA, schoolB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?, ?)",
                adminA, adminB, studentA, studentB, superAdmin);
    }

    protected Fixture insertResult(
            UUID schoolId,
            String activityTitle,
            String executionStatus,
            String internalStatus,
            String publicStatus,
            boolean blocked,
            Integer candidateVersion,
            Integer internalVersion,
            Integer publicVersion) {
        UUID activityId = insertActivity(schoolId, activityTitle, executionStatus);
        UUID resultId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_results(
                    id, school_id, activity_id, result_internal_status, result_public_status,
                    public_visibility_blocked
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, resultId, schoolId, activityId, internalStatus, publicStatus, blocked);

        UUID v1 = insertVersion(resultId, 1, "V1 " + activityTitle);
        UUID v2 = insertVersion(resultId, 2, "V2 " + activityTitle);
        UUID v3 = insertVersion(resultId, 3, "V3 " + activityTitle);
        jdbc.update("""
                UPDATE activity_results
                SET current_candidate_version_id = ?, current_internal_version_id = ?,
                    current_public_version_id = ?
                WHERE id = ?
                """, version(candidateVersion, v1, v2, v3), version(internalVersion, v1, v2, v3),
                version(publicVersion, v1, v2, v3), resultId);
        return new Fixture(activityId, resultId, v1, v2, v3);
    }

    protected UUID insertActivityWithoutResult(UUID schoolId, String title, String executionStatus) {
        return insertActivity(schoolId, title, executionStatus);
    }

    protected void setUpdatedAt(UUID resultId, Instant updatedAt) {
        jdbc.update("UPDATE activity_results SET updated_at = ? WHERE id = ?",
                Timestamp.from(updatedAt), resultId);
    }

    protected void appendReview(
            Fixture fixture, String action, UUID versionId, String reason, Instant occurredAt) {
        UUID id = UUID.randomUUID();
        if ("SUBMITTED".equals(action)) {
            jdbc.update("""
                    INSERT INTO result_review_records(
                        id, result_id, result_version_id, action, submitted_by, submitted_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, fixture.resultId(), versionId, action, adminA,
                    Timestamp.from(occurredAt), Timestamp.from(occurredAt));
            return;
        }
        jdbc.update("""
                INSERT INTO result_review_records(
                    id, result_id, result_version_id, action, reviewer_id, reviewed_at, reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, fixture.resultId(), versionId, action, superAdmin,
                Timestamp.from(occurredAt), reason, Timestamp.from(occurredAt));
    }

    protected RequestPostProcessor adminA() {
        return principal(adminA, "SCHOOL_ADMIN", adminAMembership, schoolA, "SCHOOL_ADMIN");
    }

    protected RequestPostProcessor adminB() {
        return principal(adminB, "SCHOOL_ADMIN", adminBMembership, schoolB, "SCHOOL_ADMIN");
    }

    protected RequestPostProcessor studentA() {
        return principal(studentA, "STUDENT", studentAMembership, schoolA, "STUDENT");
    }

    protected RequestPostProcessor studentB() {
        return principal(studentB, "STUDENT", studentBMembership, schoolB, "STUDENT");
    }

    protected RequestPostProcessor superAdmin() {
        return principal(superAdmin, "SUPER_ADMIN", null, null, null);
    }

    private UUID insertActivity(UUID schoolId, String title, String executionStatus) {
        UUID id = UUID.randomUUID();
        UUID creator = schoolId.equals(schoolA) ? adminA : adminB;
        jdbc.update("""
                INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by)
                VALUES (?, ?, ?, ?, 'NOT_SUBMITTED', ?)
                """, id, schoolId, title, executionStatus, creator);
        return id;
    }

    private UUID insertVersion(UUID resultId, int versionNumber, String title) {
        UUID id = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO result_versions(
                    id, result_id, version_number, title, summary_text, score_highlights,
                    media_refs, published_internally_at, published_publicly_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), now(), now())
                """, id, resultId, versionNumber, title, "Summary " + title,
                "[\"highlight " + versionNumber + "\"]", "[\"" + mediaId + "\"]");
        return id;
    }

    private UUID version(Integer number, UUID v1, UUID v2, UUID v3) {
        if (number == null) return null;
        return switch (number) {
            case 1 -> v1;
            case 2 -> v2;
            case 3 -> v3;
            default -> throw new IllegalArgumentException("unsupported fixture version");
        };
    }

    private UUID insertSchool(String label) {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, prefix + "-" + label, "USCC", prefix.substring(0, 8) + suffix + "u",
                prefix.substring(0, 8) + suffix + "i", "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", prefix + "-" + label + "@example.com", "NORMAL");
        return id;
    }

    private UUID insertUser(String label, String platformRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id, username, password_hash, account_status, platform_role)
                VALUES (?, ?, '{noop}password', 'NORMAL', ?)
                """, id, prefix + "-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), platformRole);
        return id;
    }

    private UUID insertMembership(UUID userId, UUID schoolId, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, id, userId, schoolId, role);
        return id;
    }

    private RequestPostProcessor principal(
            UUID userId, String role, UUID membershipId, UUID schoolId, String membershipRole) {
        List<AuthenticatedSchoolMembership> memberships = membershipId == null
                ? List.of()
                : List.of(new AuthenticatedSchoolMembership(membershipId, schoolId, membershipRole));
        var details = new CampusGuinnessUserDetails(
                userId, prefix + "-principal", "{noop}password", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)), memberships);
        return user(details);
    }

    protected record Fixture(UUID activityId, UUID resultId, UUID v1, UUID v2, UUID v3) {
    }
}
