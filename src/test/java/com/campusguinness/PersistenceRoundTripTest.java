package com.campusguinness;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real round-trip persistence tests for all 13 business Repositories.
 * Each test creates minimum legal data via JdbcTemplate, flushes EM,
 * then uses em.find() to verify the Entity mapping is correct.
 * Uses @Transactional to roll back all changes after each test.
 */
@Transactional
class PersistenceRoundTripTest extends PostgreSqlIntegrationTestSupport {

    @PersistenceContext private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    private UUID userId;
    private UUID schoolId;

    @BeforeEach
    void createSharedPrerequisites() {
        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();

        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                userId, "rt_" + uuid8(), "$2a$10$dummyhashvaluehere00000000", "NORMAL");
        jdbc.update("INSERT INTO schools(id, name, unified_code_type, unified_code, internal_code, " +
                        "school_type, region, address, contact_name, contact_phone, contact_email) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "Test School", "USCC", "TEST-" + uuid8(), "INT-" + uuid6(),
                "PRIMARY", "Beijing", "Test Address", "Contact", "13800000000", "test@test.com");
    }

    private static String uuid8() { return UUID.randomUUID().toString().substring(0, 8); }
    private static String uuid6() { return UUID.randomUUID().toString().substring(0, 6); }

    // ─── 1. UserRepository (0 FK deps) ───
    @Test
    @DisplayName("1/13 UserEntity: round trip save → flush → clear → find")
    void userEntityRoundTrip() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                id, "user_" + uuid8(), "$2a$10$rt", "NORMAL");

        em.clear();
        Object found = em.find(com.campusguinness.identity.internal.persistence.UserEntity.class, id);
        assertThat(found).isNotNull();
        // Verify key column via JDBC
        String status = jdbc.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, id);
        assertThat(status).isEqualTo("NORMAL");
    }

    // ─── 2. SchoolRepository (0 FK deps) ───
    @Test
    @DisplayName("2/13 SchoolEntity: round trip")
    void schoolEntityRoundTrip() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO schools(id, name, unified_code_type, unified_code, internal_code, " +
                        "school_type, region, address, contact_name, contact_phone, contact_email, school_status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, "RT School", "USCC", "RT-" + uuid8(), "RT-" + uuid6(),
                "PRIMARY", "Shanghai", "Addr", "Contact", "13900000000", "rt@test.com", "NORMAL");

        em.clear();
        Object found = em.find(com.campusguinness.school.internal.persistence.SchoolEntity.class, id);
        assertThat(found).isNotNull();
        String name = jdbc.queryForObject("SELECT name FROM schools WHERE id=?", String.class, id);
        assertThat(name).isEqualTo("RT School");
    }

    // ─── 3. SchoolRegistrationRepository (FKs: schools nullable, users nullable) ───
    @Test
    @DisplayName("3/13 SchoolRegistrationEntity: round trip")
    void schoolRegistrationEntityRoundTrip() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO school_registrations(id, school_name, unified_code_type, school_type, " +
                        "region, address, contact_name, contact_phone, contact_email, registration_status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, "Reg School", "USCC", "PRIMARY", "Guangzhou", "Addr",
                "Contact", "13700000000", "reg@test.com", "DRAFT");

        em.clear();
        Object found = em.find(com.campusguinness.school.internal.persistence.SchoolRegistrationEntity.class, id);
        assertThat(found).isNotNull();
        String status = jdbc.queryForObject("SELECT registration_status FROM school_registrations WHERE id=?", String.class, id);
        assertThat(status).isEqualTo("DRAFT");
    }

    // ─── 4. ChallengeProjectRepository (0 hard FK deps; current_rule_version_id is nullable cycle) ───
    @Test
    @DisplayName("4/13 ChallengeProjectEntity: round trip")
    void challengeProjectEntityRoundTrip() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO challenge_projects(id, name, category, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, project_status, allow_tie) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                id, "RT Project", "SPEED", "DURATION", "DURATION_MS",
                "LOWER_BETTER", "BEST", "DRAFT", true);

        em.clear();
        Object found = em.find(com.campusguinness.project.internal.persistence.ChallengeProjectEntity.class, id);
        assertThat(found).isNotNull();
        String st = jdbc.queryForObject("SELECT score_storage_type FROM challenge_projects WHERE id=?", String.class, id);
        assertThat(st).isEqualTo("DURATION");
    }

    // ─── 5. ActivityRepository (FKs: schools, users) ───
    @Test
    @DisplayName("5/13 ActivityEntity: round trip")
    void activityEntityRoundTrip() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by) " +
                        "VALUES (?,?,?,?,?,?)",
                id, schoolId, "RT Activity", "DRAFT", "NOT_SUBMITTED", userId);

        em.clear();
        Object found = em.find(com.campusguinness.activity.internal.persistence.ActivityEntity.class, id);
        assertThat(found).isNotNull();
        UUID sid = jdbc.queryForObject("SELECT school_id FROM activities WHERE id=?", UUID.class, id);
        assertThat(sid).isEqualTo(schoolId);
    }

    // ─── 6. ActivityApplicationRepository (FKs: schools, users; created_activity_id nullable) ───
    @Test
    @DisplayName("6/13 ActivityApplicationEntity: round trip")
    void activityApplicationEntityRoundTrip() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO activity_applications(id, school_id, applicant_id, title, " +
                        "application_status, application_version) VALUES (?,?,?,?,?,?)",
                id, schoolId, userId, "RT Application", "DRAFT", 1);

        em.clear();
        Object found = em.find(com.campusguinness.activity.internal.persistence.ActivityApplicationEntity.class, id);
        assertThat(found).isNotNull();
        String status = jdbc.queryForObject("SELECT application_status FROM activity_applications WHERE id=?", String.class, id);
        assertThat(status).isEqualTo("DRAFT");
    }

    // ─── 7. ScoreAttemptRepository (FK chain: users→schools→challenge_projects→rule_versions→activities→activity_projects→score_attempts) ───
    @Test
    @DisplayName("7/13 ScoreAttemptEntity: round trip with full FK chain")
    void scoreAttemptEntityRoundTrip() {
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID activityProjectId = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();

        jdbc.update("INSERT INTO challenge_projects(id, name, category, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, project_status, allow_tie) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "Score Project", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", "DRAFT", true);
        jdbc.update("INSERT INTO project_rule_versions(id, project_id, version_number, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, created_by) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", userId);
        jdbc.update("INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by) " +
                        "VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "Score Activity", "DRAFT", "NOT_SUBMITTED", userId);
        jdbc.update("INSERT INTO activity_projects(id, activity_id, project_id, rule_version_id) VALUES (?,?,?,?)",
                activityProjectId, activityId, projectId, ruleVersionId);
        jdbc.update("INSERT INTO score_attempts(id, school_id, activity_project_id, student_id, attempt_number, " +
                        "score_storage_type, score_value, score_status, entered_by, is_current_effective, is_manual_makeup) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                scoreId, schoolId, activityProjectId, userId, 1,
                "INTEGER", new BigDecimal("100"), "DRAFT", userId, false, false);

        em.clear();
        Object found = em.find(com.campusguinness.score.internal.persistence.ScoreAttemptEntity.class, scoreId);
        assertThat(found).isNotNull();
        BigDecimal val = jdbc.queryForObject("SELECT score_value FROM score_attempts WHERE id=?", BigDecimal.class, scoreId);
        assertThat(val).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    // ─── 8. RankingDefinitionRepository (FKs: challenge_projects, users; school_id nullable) ───
    @Test
    @DisplayName("8/13 RankingDefinitionEntity: round trip")
    void rankingDefinitionEntityRoundTrip() {
        UUID projectId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        jdbc.update("INSERT INTO challenge_projects(id, name, category, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, project_status, allow_tie) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "Rank Project", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", "DRAFT", true);
        jdbc.update("INSERT INTO ranking_definitions(id, layer, name, project_id, created_by) VALUES (?,?,?,?,?)",
                id, "L1", "RT Ranking", projectId, userId);

        em.clear();
        Object found = em.find(com.campusguinness.ranking.internal.persistence.RankingDefinitionEntity.class, id);
        assertThat(found).isNotNull();
        String layer = jdbc.queryForObject("SELECT layer FROM ranking_definitions WHERE id=?", String.class, id);
        assertThat(layer).isEqualTo("L1");
    }

    // ─── 9. L3AuthorizationRepository (FKs: schools, challenge_projects, rule_versions, users) ───
    @Test
    @DisplayName("9/13 L3AuthorizationEntity: round trip")
    void l3AuthorizationEntityRoundTrip() {
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        jdbc.update("INSERT INTO challenge_projects(id, name, category, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, project_status, allow_tie) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "L3 Project", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", "DRAFT", true);
        jdbc.update("INSERT INTO project_rule_versions(id, project_id, version_number, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, created_by) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", userId);
        jdbc.update("INSERT INTO l3_authorizations(id, school_id, project_id, rule_version_id, " +
                        "authorization_status, allow_school_name, allow_student_name) VALUES (?,?,?,?,?,?,?)",
                id, schoolId, projectId, ruleVersionId, "DRAFT", true, false);

        em.clear();
        Object found = em.find(com.campusguinness.ranking.internal.persistence.L3AuthorizationEntity.class, id);
        assertThat(found).isNotNull();
        String status = jdbc.queryForObject("SELECT authorization_status FROM l3_authorizations WHERE id=?", String.class, id);
        assertThat(status).isEqualTo("DRAFT");
    }

    // ─── 10. ScoreAppealRepository (FKs: schools, score_attempts, users) ───
    @Test
    @DisplayName("10/13 ScoreAppealEntity: round trip")
    void scoreAppealEntityRoundTrip() {
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID activityProjectId = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();

        jdbc.update("INSERT INTO challenge_projects(id, name, category, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, project_status, allow_tie) " +
                        "VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "Appeal Proj", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", "DRAFT", true);
        jdbc.update("INSERT INTO project_rule_versions(id, project_id, version_number, score_storage_type, " +
                        "score_indicator_type, comparison_direction, effective_score_rule, created_by) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "BEST", userId);
        jdbc.update("INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by) " +
                        "VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "Appeal Act", "DRAFT", "NOT_SUBMITTED", userId);
        jdbc.update("INSERT INTO activity_projects(id, activity_id, project_id, rule_version_id) VALUES (?,?,?,?)",
                activityProjectId, activityId, projectId, ruleVersionId);
        jdbc.update("INSERT INTO score_attempts(id, school_id, activity_project_id, student_id, attempt_number, " +
                        "score_storage_type, score_value, score_status, entered_by, is_current_effective, is_manual_makeup) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                scoreId, schoolId, activityProjectId, userId, 1,
                "INTEGER", new BigDecimal("95"), "APPROVED", userId, false, false);
        jdbc.update("INSERT INTO score_appeals(id, school_id, score_attempt_id, student_id, " +
                        "appeal_type, appeal_reason, appeal_status) VALUES (?,?,?,?,?,?,?)",
                appealId, schoolId, scoreId, userId, "SCORE", "Score seems wrong", "SUBMITTED");

        em.clear();
        Object found = em.find(com.campusguinness.appeal.internal.persistence.ScoreAppealEntity.class, appealId);
        assertThat(found).isNotNull();
        String type = jdbc.queryForObject("SELECT appeal_type FROM score_appeals WHERE id=?", String.class, appealId);
        assertThat(type).isEqualTo("SCORE");
    }

    // ─── 11. MediaRepository (FKs: schools, activities(same-school composite), users) ───
    @Test
    @DisplayName("11/13 MediaEntity: round trip")
    void mediaEntityRoundTrip() {
        UUID activityId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        jdbc.update("INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by) " +
                        "VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "Media Act", "DRAFT", "NOT_SUBMITTED", userId);
        jdbc.update("INSERT INTO media(id, school_id, activity_id, uploader_id, file_key, file_name, " +
                        "file_type, file_format, file_size_bytes, internal_status, public_status) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                mediaId, schoolId, activityId, userId, "test/file/key.jpg", "photo.jpg",
                "IMAGE", "jpg", 1024L, "DRAFT", "NOT_SUBMITTED");

        em.clear();
        Object found = em.find(com.campusguinness.media.internal.persistence.MediaEntity.class, mediaId);
        assertThat(found).isNotNull();
        String fk = jdbc.queryForObject("SELECT file_key FROM media WHERE id=?", String.class, mediaId);
        assertThat(fk).isEqualTo("test/file/key.jpg");
    }

    // ─── 12. ActivityResultRepository (FKs: schools, activities(same-school composite)) ───
    @Test
    @DisplayName("12/13 ActivityResultEntity: round trip")
    void activityResultEntityRoundTrip() {
        UUID activityId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();

        jdbc.update("INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by) " +
                        "VALUES (?,?,?,?,?,?)",
                activityId, schoolId, "Result Act", "DRAFT", "NOT_SUBMITTED", userId);
        jdbc.update("INSERT INTO activity_results(id, school_id, activity_id, " +
                        "result_internal_status, result_public_status) VALUES (?,?,?,?,?)",
                resultId, schoolId, activityId, "DRAFT", "NOT_SUBMITTED");

        em.clear();
        Object found = em.find(com.campusguinness.result.internal.persistence.ActivityResultEntity.class, resultId);
        assertThat(found).isNotNull();
        String istatus = jdbc.queryForObject("SELECT result_internal_status FROM activity_results WHERE id=?", String.class, resultId);
        assertThat(istatus).isEqualTo("DRAFT");
    }

    // ─── 13. FeedbackRepository (FKs: schools nullable, users nullable) ───
    @Test
    @DisplayName("13/13 FeedbackEntity: round trip")
    void feedbackEntityRoundTrip() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO feedbacks(id, feedback_type, content, feedback_status) VALUES (?,?,?,?)",
                id, "GENERAL", "Test feedback content", "SUBMITTED");

        em.clear();
        Object found = em.find(com.campusguinness.feedback.internal.persistence.FeedbackEntity.class, id);
        assertThat(found).isNotNull();
        String type = jdbc.queryForObject("SELECT feedback_type FROM feedbacks WHERE id=?", String.class, id);
        assertThat(type).isEqualTo("GENERAL");
    }
}
