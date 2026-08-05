package com.campusguinness.identity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.port.SchoolAdminInvitationRepository;
import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationStatus;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Identity application and invitation persistence")
class IdentityApplicationInvitationPersistenceIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StudentIdentityApplicationRepository studentApplications;
    @Autowired private SchoolAdminInvitationRepository schoolAdminInvitations;

    private final String runPrefix = "phase2-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID studentUserId;
    private UUID teacherUserId;
    private UUID superAdminId;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        studentUserId = UUID.randomUUID();
        teacherUserId = UUID.randomUUID();
        superAdminId = UUID.randomUUID();
        schoolId = UUID.randomUUID();

        insertUser(studentUserId, username("student"), "PENDING_ACTIVATION", null);
        insertUser(teacherUserId, username("teacher"), "PENDING_ACTIVATION", null);
        insertUser(superAdminId, username("super"), "NORMAL", "SUPER_ADMIN");
        insertSchool(schoolId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM school_admin_invitations WHERE user_id IN (?,?,?)",
                studentUserId, teacherUserId, superAdminId);
        jdbc.update("DELETE FROM student_identity_applications WHERE user_id IN (?,?,?)",
                studentUserId, teacherUserId, superAdminId);
        jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", studentUserId, teacherUserId, superAdminId);
    }

    @Test
    @DisplayName("saves and restores a pending student identity application without membership side effects")
    void savesAndRestoresPendingStudentApplication() {
        var applicationId = new StudentIdentityApplicationId(UUID.randomUUID());
        var application = StudentIdentityApplication.create(new StudentIdentityApplication.Builder()
                .id(applicationId)
                .userId(studentUserId)
                .schoolId(schoolId)
                .realName("Student Name")
                .studentNumber("S-001")
                .grade("G7")
                .className("Class 1")
                .evidenceFileKey("proof.pdf"));

        studentApplications.save(application);

        var restored = studentApplications.findById(applicationId);
        assertThat(restored).isPresent();
        assertThat(restored.get().status()).isEqualTo(StudentIdentityApplicationStatus.PENDING);
        assertThat(restored.get().schoolId()).isEqualTo(schoolId);
        assertThat(countMemberships(studentUserId)).isZero();
    }

    @Test
    @DisplayName("saves and restores a rejected student identity application with review data")
    void savesAndRestoresRejectedStudentApplication() {
        var applicationId = new StudentIdentityApplicationId(UUID.randomUUID());
        var reviewedAt = Instant.parse("2026-08-05T10:15:30Z");
        var application = StudentIdentityApplication.create(new StudentIdentityApplication.Builder()
                .id(applicationId)
                .userId(studentUserId)
                .schoolId(schoolId)
                .realName("Student Name")
                .studentNumber("S-001")
                .grade("G7")
                .className("Class 1"));
        application.reject(superAdminId, reviewedAt, "number mismatch");

        studentApplications.save(application);

        var restored = studentApplications.findById(applicationId).orElseThrow();
        assertThat(restored.status()).isEqualTo(StudentIdentityApplicationStatus.REJECTED);
        assertThat(restored.reviewedBy()).isEqualTo(superAdminId);
        assertThat(restored.reviewedAt()).isEqualTo(reviewedAt);
        assertThat(restored.rejectionReason()).isEqualTo("number mismatch");
    }

    @Test
    @DisplayName("saves and restores a pending school admin invitation bound to user and school")
    void savesAndRestoresPendingSchoolAdminInvitation() {
        var invitationId = new SchoolAdminInvitationId(UUID.randomUUID());
        var invitation = SchoolAdminInvitation.create(new SchoolAdminInvitation.Builder()
                .id(invitationId)
                .userId(teacherUserId)
                .schoolId(schoolId)
                .invitationCodeHash("$2a$10$hashedInvite")
                .expiresAt(Instant.parse("2026-08-06T00:00:00Z"))
                .createdBy(superAdminId));

        schoolAdminInvitations.save(invitation);

        var restored = schoolAdminInvitations.findById(invitationId);
        assertThat(restored).isPresent();
        assertThat(restored.get().status()).isEqualTo(SchoolAdminInvitationStatus.PENDING);
        assertThat(restored.get().roleInSchool()).isEqualTo("SCHOOL_ADMIN");
        assertThat(restored.get().schoolId()).isEqualTo(schoolId);
        assertThat(restored.get().invitationCodeHash()).isEqualTo("$2a$10$hashedInvite");
        assertThat(countMemberships(teacherUserId)).isZero();
    }

    private void insertUser(UUID id, String username, String status, String platformRole) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, username, "hash", status, platformRole);
    }

    private void insertSchool(UUID id) {
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, "Phase2 School", "USCC", runPrefix + "-code", runPrefix + "-internal",
                "PRIMARY", "Beijing", "Address", "Contact", "13800000000",
                "phase2@example.com", "NORMAL");
    }

    private int countMemberships(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM school_memberships WHERE user_id = ?",
                Integer.class, userId);
    }

    private String username(String label) {
        return runPrefix + "-" + label;
    }
}
