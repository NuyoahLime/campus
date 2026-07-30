package com.campusguinness.score.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.score.application.query.port.SchoolAdminScoreQueryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolAdminScoreQueryAdapterIT extends PostgreSqlIntegrationTestSupport {
    @Autowired SchoolAdminScoreQueryPort query;
    @Autowired JdbcTemplate jdbc;

    private UUID schoolId;
    private UUID otherSchoolId;
    private UUID adminId;
    private UUID studentId;
    private UUID entrantId;
    private UUID activityId;
    private UUID projectId;
    private UUID ruleVersionId;
    private UUID activityProjectId;
    private UUID attemptId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        schoolId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        adminId = user("admin-" + suffix);
        studentId = user("student-" + suffix);
        entrantId = user("entrant-" + suffix);
        school(schoolId, "School " + suffix);
        school(otherSchoolId, "Other " + suffix);
        membership(adminId, schoolId, "SCHOOL_ADMIN");
        membership(studentId, schoolId, "STUDENT");

        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO challenge_projects(
                  id,name,category,score_storage_type,score_indicator_type,
                  comparison_direction,score_unit,decimal_places,allow_tie,
                  effective_score_rule,project_status,created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, projectId, "Jump " + suffix, "SPORT", "INTEGER", "COUNT",
                "HIGHER_BETTER", "次", 0, true, "BEST", "PUBLISHED",
                ts(Instant.now()), ts(Instant.now()), 1);
        jdbc.update("""
                INSERT INTO project_rule_versions(
                  id,project_id,version_number,score_storage_type,score_indicator_type,
                  comparison_direction,effective_score_rule,score_unit,decimal_places,
                  created_by,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, ruleVersionId, projectId, 1, "INTEGER", "COUNT",
                "HIGHER_BETTER", "BEST", "次", 0, adminId, ts(Instant.now()));
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id=? WHERE id=?",
                ruleVersionId, projectId);

        activityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(
                  id,school_id,title,execution_status,public_status,created_by,
                  created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, activityId, schoolId, "Sports Day " + suffix, "PUBLISHED",
                "NOT_SUBMITTED", adminId, ts(Instant.now()), ts(Instant.now()), 1);
        activityProjectId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activity_projects(id,activity_id,project_id,rule_version_id,created_at)
                VALUES (?,?,?,?,?)
                """, activityProjectId, activityId, projectId, ruleVersionId, ts(Instant.now()));
        attemptId = attempt("PENDING_REVIEW", studentId, entrantId, Instant.now(), Instant.now());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("""
                DELETE FROM score_review_records
                WHERE score_attempt_id IN (
                  SELECT id FROM score_attempts WHERE school_id IN (?,?))
                """, schoolId, otherSchoolId);
        jdbc.update("DELETE FROM score_attempts WHERE school_id IN (?,?)", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM activity_projects WHERE activity_id=?", activityId);
        jdbc.update("DELETE FROM activities WHERE id=?", activityId);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id=NULL WHERE id=?", projectId);
        jdbc.update("DELETE FROM project_rule_versions WHERE project_id=?", projectId);
        jdbc.update("DELETE FROM challenge_projects WHERE id=?", projectId);
        jdbc.update("DELETE FROM school_memberships WHERE school_id IN (?,?)", schoolId, otherSchoolId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", adminId, studentId, entrantId);
        jdbc.update("DELETE FROM schools WHERE id IN (?,?)", schoolId, otherSchoolId);
    }

    @Test
    void defaultListReturnsPendingReviewOnly() {
        attempt("APPROVED", studentId, entrantId, Instant.now(), Instant.now());
        var page = list("PENDING_REVIEW", null, null, null, 0, 20);
        assertThat(page.items()).extracting("status").containsOnly("PENDING_REVIEW");
    }

    @Test
    void listReturnsOnlyActorSchoolScores() {
        attemptWithSchool(otherSchoolId, "PENDING_REVIEW", studentId, entrantId,
                Instant.now(), Instant.now());
        assertThat(list("PENDING_REVIEW", null, null, null, 0, 20).items())
                .extracting("schoolId").containsOnly(schoolId);
    }

    @Test
    void statusFilterWorks() {
        UUID approved = attempt("APPROVED", studentId, entrantId, Instant.now(), Instant.now());
        assertThat(list("APPROVED", null, null, null, 0, 20).items())
                .extracting("attemptId").containsExactly(approved);
    }

    @Test
    void activityFilterWorks() {
        assertThat(list("PENDING_REVIEW", activityId, null, null, 0, 20).totalElements()).isEqualTo(1);
        assertThat(list("PENDING_REVIEW", UUID.randomUUID(), null, null, 0, 20).totalElements()).isZero();
    }

    @Test
    void projectFilterWorks() {
        assertThat(list("PENDING_REVIEW", null, projectId, null, 0, 20).totalElements()).isEqualTo(1);
        assertThat(list("PENDING_REVIEW", null, UUID.randomUUID(), null, 0, 20).totalElements()).isZero();
    }

    @Test
    void keywordMatchesStudentUsername() {
        assertThat(list("PENDING_REVIEW", null, null, "STUDENT-" + suffix, 0, 20)
                .totalElements()).isEqualTo(1);
    }

    @Test
    void keywordMatchesEntrantUsername() {
        assertThat(list("PENDING_REVIEW", null, null, "ENTRANT-" + suffix, 0, 20)
                .totalElements()).isEqualTo(1);
    }

    @Test
    void keywordMatchesActivityTitle() {
        assertThat(list("PENDING_REVIEW", null, null, "SPORTS DAY", 0, 20)
                .totalElements()).isEqualTo(1);
    }

    @Test
    void paginationReturnsCorrectTotal() {
        attempt("PENDING_REVIEW", studentId, entrantId, Instant.now(), Instant.now());
        attempt("PENDING_REVIEW", studentId, entrantId, Instant.now(), Instant.now());
        var page = list("PENDING_REVIEW", null, null, null, 1, 2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void stableOrderingUsesSubmittedCreatedAndId() {
        jdbc.update("DELETE FROM score_attempts WHERE school_id=?", schoolId);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        UUID older = attempt("PENDING_REVIEW", studentId, entrantId, base, base);
        UUID newer = attempt("PENDING_REVIEW", studentId, entrantId, base.plusSeconds(1), base);
        UUID noSubmission = attempt("PENDING_REVIEW", studentId, entrantId, null, base.plusSeconds(100));
        assertThat(list("PENDING_REVIEW", null, null, null, 0, 20).items())
                .extracting("attemptId")
                .containsExactly(newer, older, noSubmission);
    }

    @Test
    void detailReturnsRawAndFormattedScore() {
        jdbc.update("""
                UPDATE score_attempts
                SET score_storage_type='DECIMAL', score_value=12.3456
                WHERE id=?
                """, attemptId);
        jdbc.update("""
                UPDATE challenge_projects
                SET score_storage_type='DECIMAL', decimal_places=2
                WHERE id=?
                """, projectId);
        var detail = query.findDetail(schoolId, attemptId).orElseThrow();
        assertThat(detail.decimalValue()).isEqualByComparingTo("12.3456");
        assertThat(detail.integerValue()).isNull();
        assertThat(detail.displayValue()).isEqualTo("12.35");
    }

    @Test
    void detailReturnsReviewHistory() {
        UUID recordId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO score_review_records(
                  id,score_attempt_id,reviewer_id,review_result,review_comment,reviewed_at)
                VALUES (?,?,?,?,?,?)
                """, recordId, attemptId, adminId, "APPROVED", "looks good", ts(Instant.now()));
        var detail = query.findDetail(schoolId, attemptId).orElseThrow();
        assertThat(detail.reviewHistory()).hasSize(1);
        assertThat(detail.reviewHistory().getFirst().reviewRecordId()).isEqualTo(recordId);
    }

    @Test
    void otherSchoolAttemptLooksNotFound() {
        UUID otherAttempt = attemptWithSchool(otherSchoolId, "PENDING_REVIEW",
                studentId, entrantId, Instant.now(), Instant.now());
        assertThat(query.findDetail(schoolId, otherAttempt)).isEmpty();
    }

    @Test
    void queryDoesNotDuplicateAttemptWhenHistoryHasMultipleRows() {
        for (int i = 0; i < 2; i++) {
            jdbc.update("""
                    INSERT INTO score_review_records(
                      id,score_attempt_id,reviewer_id,review_result,reviewed_at)
                    VALUES (?,?,?,?,?)
                    """, UUID.randomUUID(), attemptId, adminId,
                    i == 0 ? "APPROVED" : "REJECTED", ts(Instant.now().plusSeconds(i)));
        }
        var page = list("PENDING_REVIEW", null, null, null, 0, 20);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(query.findDetail(schoolId, attemptId).orElseThrow().reviewHistory()).hasSize(2);
    }

    private com.campusguinness.project.application.query.model.QueryPage<com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptItem> list(
            String status, UUID activity, UUID project, String keyword, int page, int size) {
        return query.findBySchool(schoolId, status, activity, project, keyword, page, size);
    }

    private UUID attempt(String status, UUID student, UUID entrant, Instant submitted, Instant created) {
        return attemptWithSchool(schoolId, status, student, entrant, submitted, created);
    }

    private UUID attemptWithSchool(
            UUID attemptSchool, String status, UUID student, UUID entrant,
            Instant submitted, Instant created) {
        UUID id = UUID.randomUUID();
        int number = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_number),0)+1
                FROM score_attempts
                WHERE activity_project_id=? AND student_id=?
                """, Integer.class, activityProjectId, student);
        jdbc.update("""
                INSERT INTO score_attempts(
                  id,school_id,activity_project_id,student_id,attempt_number,
                  score_storage_type,score_value,score_business_time,time_source,
                  is_current_effective,score_status,entered_by,submitted_at,
                  is_manual_makeup,created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, attemptSchool, activityProjectId, student, number,
                "INTEGER", 100, ts(Instant.now()), "TEACHER", false, status,
                entrant, submitted == null ? null : ts(submitted), false,
                ts(created), ts(created), 1);
        return id;
    }

    private UUID user(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,username,password_hash,account_status)
                VALUES (?,?,?,?)
                """, id, username, "$2a$10$hash0000000000000000000000", "NORMAL");
        return id;
    }

    private void school(UUID id, String name) {
        jdbc.update("""
                INSERT INTO schools(
                  id,name,unified_code_type,unified_code,internal_code,school_type,
                  region,address,contact_name,contact_phone,contact_email,school_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, name, "USCC", "UC-" + id.toString().substring(0, 8),
                "IC-" + id.toString().substring(0, 8), "PRIMARY",
                "Region", "Address", "Contact", "123", "test@example.com", "NORMAL");
    }

    private void membership(UUID user, UUID school, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(
                  id,user_id,school_id,role_in_school,status,started_at,created_at,version)
                VALUES (?,?,?,?,?,now(),now(),1)
                """, UUID.randomUUID(), user, school, role, "ACTIVE");
    }

    private static Timestamp ts(Instant value) {
        return Timestamp.from(value);
    }
}
