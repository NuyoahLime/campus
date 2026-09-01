package com.campusguinness.ranking.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.ranking.application.query.model.RankingEntryReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadResult;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingGenerationApplicationServiceIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private RankingDefinitionApplicationService rankingDefinitions;
    @Autowired private RankingGenerationApplicationService rankingGeneration;
    @Autowired private com.campusguinness.ranking.application.service.RankingReadQueryService rankingRead;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private final String runPrefix = "phase-rank-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID enteredBy;
    private UUID studentA;
    private UUID studentB;
    private UUID studentC;
    private UUID studentD;
    private UUID studentCrossSchool;
    private UUID draftStudent;
    private UUID pendingStudent;
    private UUID rejectedStudent;
    private UUID projectId;
    private UUID ruleVersionId;
    private UUID activityId;
    private UUID activityProjectId;
    private UsernamePasswordAuthenticationToken adminAuthentication;

    @BeforeEach
    void setUp() {
        schoolA = UUID.randomUUID();
        schoolB = UUID.randomUUID();
        adminA = UUID.randomUUID();
        enteredBy = UUID.randomUUID();
        studentA = UUID.fromString("00000000-0000-0000-0000-000000000011");
        studentB = UUID.fromString("00000000-0000-0000-0000-000000000012");
        studentC = UUID.fromString("00000000-0000-0000-0000-000000000013");
        studentD = UUID.fromString("00000000-0000-0000-0000-000000000014");
        studentCrossSchool = UUID.fromString("00000000-0000-0000-0000-000000000021");
        draftStudent = UUID.fromString("00000000-0000-0000-0000-000000000031");
        pendingStudent = UUID.fromString("00000000-0000-0000-0000-000000000033");
        rejectedStudent = UUID.fromString("00000000-0000-0000-0000-000000000032");
        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        activityProjectId = UUID.randomUUID();

        insertSchool(schoolA, "school-a");
        insertSchool(schoolB, "school-b");
        insertUser(adminA, "admin-a");
        insertUser(enteredBy, "entered-by");
        insertUser(studentA, "student-a");
        insertUser(studentB, "student-b");
        insertUser(studentC, "student-c");
        insertUser(studentD, "student-d");
        insertUser(studentCrossSchool, "student-cross");
        insertUser(draftStudent, "student-draft");
        insertUser(pendingStudent, "student-pending");
        insertUser(rejectedStudent, "student-rejected");
        insertMembership(UUID.randomUUID(), adminA, schoolA, "SCHOOL_ADMIN");
        insertMembership(UUID.randomUUID(), studentA, schoolA, "STUDENT");
        insertProject(projectId, ruleVersionId, "INTEGER", "HIGHER_BETTER", "pts");
        insertActivity(activityId, schoolA, enteredBy);
        insertActivityProject(activityProjectId, activityId, projectId, ruleVersionId);
        authenticateSchoolAdmin(adminA, schoolA);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbc.update("UPDATE ranking_definitions SET current_version_id = null WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("""
                DELETE FROM ranking_entry_score_sources
                WHERE entry_id IN (
                    SELECT id FROM ranking_entries
                    WHERE version_id IN (
                        SELECT id FROM ranking_versions
                        WHERE definition_id IN (
                            SELECT id FROM ranking_definitions WHERE name LIKE ?
                        )
                    )
                )
                """, runPrefix + "%");
        jdbc.update("""
                DELETE FROM ranking_entries
                WHERE version_id IN (
                    SELECT id FROM ranking_versions
                    WHERE definition_id IN (
                        SELECT id FROM ranking_definitions WHERE name LIKE ?
                    )
                )
                """, runPrefix + "%");
        jdbc.update("DELETE FROM ranking_versions WHERE definition_id IN (SELECT id FROM ranking_definitions WHERE name LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM ranking_definitions WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM score_attempts WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM activity_projects WHERE activity_id IN (SELECT id FROM activities WHERE title LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM activities WHERE title LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM project_rule_versions WHERE project_id IN (SELECT id FROM challenge_projects WHERE name LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM challenge_projects WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    @Test
    void createRejectsForgedSchoolScope() {
        UUID forgedSchool = UUID.randomUUID();

        assertThatThrownBy(() -> rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + "-ranking",
                forgedSchool,
                projectId,
                activityProjectId))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo("SCHOOL_ADMIN_SCOPE_DENIED"));
    }

    @Test
    void generateUsesAuthoritativeCurrentEffectiveScoresAndTracesSources() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + "-ranking",
                schoolA,
                projectId,
                activityProjectId).id();

        UUID approvedA = insertScore(studentA, schoolA, 1, "APPROVED", true, "98", null, null);
        insertScore(studentA, schoolA, 2, "APPROVED", false, "100", null, null);
        UUID approvedB = insertScore(studentB, schoolA, 1, "APPROVED", true, "98", null, null);
        insertScore(studentC, schoolA, 1, "APPROVED", true, "95", null, null);
        insertScore(studentCrossSchool, schoolB, 1, "APPROVED", true, "99", null, null);
        insertScore(draftStudent, schoolA, 1, "DRAFT", false, "150", null, null);
        insertScore(pendingStudent, schoolA, 1, "PENDING_REVIEW", false, "145", null, null);
        insertScore(rejectedStudent, schoolA, 1, "REJECTED", false, "140", null, null);
        insertScore(studentD, schoolA, 1, "INVALIDATED", false, "130", null, null);

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.entryCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?", UUID.class, definitionId)).isNull();

        assertThatThrownBy(() -> rankingRead.publicDetail(definitionId))
                .isInstanceOf(IllegalArgumentException.class);

        List<RankingEntryReadResult> entries = jdbc.query("""
                SELECT re.rank_position, re.student_display_name, re.school_name, re.score_display_value
                FROM ranking_entries re
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ?
                ORDER BY re.rank_position ASC, re.student_id ASC
                """, (rs, row) -> new RankingEntryReadResult(
                rs.getInt("rank_position"),
                rs.getString("student_display_name"),
                rs.getString("school_name"),
                rs.getString("score_display_value")), definitionId);

        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(RankingEntryReadResult::rankPosition).containsExactly(1, 1, 3);
        assertThat(entries).extracting(RankingEntryReadResult::scoreDisplayValue).containsExactly("98", "98", "95");
        assertThat(entries).extracting(RankingEntryReadResult::studentDisplayName)
                .containsExactly(runPrefix + "-student-a", runPrefix + "-student-b", runPrefix + "-student-c");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ranking_entry_score_sources res
                JOIN ranking_entries re ON re.id = res.entry_id
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ?
                """, Integer.class, definitionId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT score_attempt_id FROM ranking_entry_score_sources res
                JOIN ranking_entries re ON re.id = res.entry_id
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ? AND re.student_id = ?
                """, UUID.class, definitionId, studentA)).isEqualTo(approvedA);
        assertThat(jdbc.queryForObject("""
                SELECT score_attempt_id FROM ranking_entry_score_sources res
                JOIN ranking_entries re ON re.id = res.entry_id
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ? AND re.student_id = ?
                """, UUID.class, definitionId, studentB)).isEqualTo(approvedB);
    }

    @Test
    void emptyRankingCreatesGeneratedVersionWithoutEntries() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + "-empty-ranking",
                schoolA,
                projectId,
                activityProjectId).id();

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.entryCount()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ranking_entries re
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ?
                """, Integer.class, definitionId)).isZero();
    }

    @Test
    void databasePreventsDuplicateCurrentEffectiveScoresForSameStudentAndActivityProject() {
        insertScore(studentA, schoolA, 1, "APPROVED", true, "98", null, null);

        assertThatThrownBy(() -> insertScore(studentA, schoolA, 2, "APPROVED", true, "99", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void generatedVersionDoesNotSwitchCurrentPointerAndPublishedReadStaysVisible() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + "-ranking",
                schoolA,
                projectId,
                activityProjectId).id();

        UUID approvedV1 = insertScore(studentA, schoolA, 1, "APPROVED", true, "98", null, null);
        var first = rankingGeneration.generate(definitionId);
        assertThat(first.versionNumber()).isEqualTo(1);

        jdbc.update("UPDATE ranking_versions SET version_status = 'PUBLISHED', published_at = ?, created_reason = 'PUBLISHED' WHERE id = ?",
                Timestamp.from(Instant.now()), first.rankingVersionId());
        jdbc.update("UPDATE ranking_definitions SET current_version_id = ? WHERE id = ?", first.rankingVersionId(), definitionId);

        RankingReadResult visibleBefore = rankingRead.publicDetail(definitionId);
        assertThat(visibleBefore.versionNumber()).isEqualTo(1);
        assertThat(visibleBefore.entries()).hasSize(1);
        assertThat(visibleBefore.entries().getFirst().scoreDisplayValue()).isEqualTo("98");

        jdbc.update("UPDATE score_attempts SET is_current_effective = false WHERE id = ?", approvedV1);
        UUID approvedV2 = insertScore(studentA, schoolA, 2, "APPROVED", true, "99", null, null);

        var second = rankingGeneration.generate(definitionId);
        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?", UUID.class, definitionId))
                .isEqualTo(first.rankingVersionId());

        RankingReadResult visibleAfter = rankingRead.publicDetail(definitionId);
        assertThat(visibleAfter.versionNumber()).isEqualTo(1);
        assertThat(visibleAfter.entries()).hasSize(1);
        assertThat(visibleAfter.entries().getFirst().scoreDisplayValue()).isEqualTo("98");
        authenticateStudent(studentA, schoolA);
        assertThat(rankingRead.studentDetail(definitionId).versionNumber()).isEqualTo(1);
        authenticateSchoolAdmin(adminA, schoolA);
        assertThat(rankingRead.schoolAdminDetail(definitionId).versionNumber()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ?
                """, Integer.class, definitionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT score_attempt_id FROM ranking_entry_score_sources res
                JOIN ranking_entries re ON re.id = res.entry_id
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.version_number = 2 AND rv.definition_id = ?
                """, UUID.class, definitionId)).isEqualTo(approvedV2);
    }

    @Test
    void concurrentGenerationAllocatesDistinctVersionNumbers() throws Exception {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + "-ranking",
                schoolA,
                projectId,
                activityProjectId).id();
        insertScore(studentA, schoolA, 1, "APPROVED", true, "98", null, null);
        insertScore(studentB, schoolA, 1, "APPROVED", true, "96", null, null);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<com.campusguinness.ranking.application.result.RankingGenerationResult> first =
                    executor.submit(() -> runConcurrent(ready, start, () -> rankingGeneration.generate(definitionId)));
            Future<com.campusguinness.ranking.application.result.RankingGenerationResult> second =
                    executor.submit(() -> runConcurrent(ready, start, () -> rankingGeneration.generate(definitionId)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var firstResult = first.get(30, TimeUnit.SECONDS);
            var secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(List.of(firstResult.versionNumber(), secondResult.versionNumber()))
                    .containsExactlyInAnyOrder(1, 2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ?", Integer.class, definitionId))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM (
                        SELECT version_number FROM ranking_versions
                        WHERE definition_id = ?
                        GROUP BY version_number
                        HAVING COUNT(*) > 1
                    ) dup
                    """, Integer.class, definitionId)).isEqualTo(0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void generationRollsBackWhenOuterTransactionFails() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + "-ranking",
                schoolA,
                projectId,
                activityProjectId).id();
        insertScore(studentA, schoolA, 1, "APPROVED", true, "98", null, null);

        TransactionTemplate tt = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tt.executeWithoutResult(status -> {
            rankingGeneration.generate(definitionId);
            throw new RuntimeException("simulated failure after generation");
        })).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ?", Integer.class, definitionId))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ranking_entries re
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ?
                """, Integer.class, definitionId)).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?", UUID.class, definitionId))
                .isNull();
    }

    @Test
    void disabledDefinitionIsRejectedByGeneration() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + "-ranking",
                schoolA,
                projectId,
                activityProjectId).id();
        insertScore(studentA, schoolA, 1, "APPROVED", true, "98", null, null);
        rankingDefinitions.disable(definitionId);

        assertThatThrownBy(() -> rankingGeneration.generate(definitionId))
                .isInstanceOf(IllegalStateException.class);
    }

    private void authenticateSchoolAdmin(UUID userId, UUID schoolId) {
        var details = new CampusGuinnessUserDetails(
                userId,
                runPrefix + "-admin",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, "SCHOOL_ADMIN")));
        adminAuthentication = new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(adminAuthentication);
    }

    private void authenticateStudent(UUID userId, UUID schoolId) {
        var details = new CampusGuinnessUserDetails(
                userId,
                runPrefix + "-student",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_STUDENT")),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, "STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }

    private void insertSchool(UUID id, String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, runPrefix + "-" + label, "USCC", runPrefix + "-" + suffix + "-uc",
                runPrefix + "-" + suffix + "-ic", "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", runPrefix + "@example.com", "NORMAL");
    }

    private void insertUser(UUID id, String label) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status) VALUES (?,?,?,?)",
                id, runPrefix + "-" + label, "{noop}password", "NORMAL");
    }

    private void insertMembership(UUID membershipId, UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, membershipId, userId, schoolId, role);
    }

    private void insertProject(UUID projectId, UUID ruleVersionId, String storageType, String direction, String unit) {
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id, name, category, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """,
                projectId, runPrefix + "-project", "SPORTS", storageType, "NUMERIC",
                direction, unit, "BEST", "PUBLISHED");
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id, project_id, version_number, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, rules_text, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                ruleVersionId, projectId, 1, storageType, "NUMERIC", direction, unit, "BEST",
                runPrefix + "-rules", enteredBy);
    }

    private void insertActivity(UUID activityId, UUID schoolId, UUID createdBy) {
        jdbc.update("""
                INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by)
                VALUES (?,?,?,?,?,?)
                """, activityId, schoolId, runPrefix + "-activity", "PUBLISHED", "PUBLIC", createdBy);
    }

    private void insertActivityProject(UUID activityProjectId, UUID activityId, UUID projectId, UUID ruleVersionId) {
        jdbc.update("INSERT INTO activity_projects(id, activity_id, project_id, rule_version_id) VALUES (?,?,?,?)",
                activityProjectId, activityId, projectId, ruleVersionId);
    }

    private UUID insertScore(UUID studentId, UUID schoolId, int attemptNumber, String status, boolean currentEffective,
                             String scoreValue, Long durationMs, String grade) {
        UUID scoreAttemptId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO score_attempts(
                    id, school_id, activity_project_id, student_id, attempt_number,
                    score_storage_type, score_value, score_duration_ms, score_grade,
                    is_current_effective, score_status, entered_by, score_business_time, version
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                scoreAttemptId, schoolId, activityProjectId, studentId, attemptNumber,
                "INTEGER", scoreValue == null ? null : new BigDecimal(scoreValue), durationMs, grade,
                currentEffective, status, enteredBy, Timestamp.from(Instant.now()), 1);
        return scoreAttemptId;
    }

    private com.campusguinness.ranking.application.result.RankingGenerationResult runConcurrent(
            CountDownLatch ready, CountDownLatch start,
            java.util.function.Supplier<com.campusguinness.ranking.application.result.RankingGenerationResult> action) {
        try {
            SecurityContextHolder.getContext().setAuthentication(adminAuthentication);
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return action.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
