package com.campusguinness.ranking.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.infrastructure.security.AuthenticatedSchoolMembership;
import com.campusguinness.infrastructure.security.CampusGuinnessUserDetails;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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

class L2RankingGenerationApplicationServiceIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private RankingDefinitionApplicationService rankingDefinitions;
    @Autowired private RankingGenerationApplicationService rankingGeneration;
    @Autowired private JdbcTemplate jdbc;

    private final String runPrefix = "phase-l2-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID enteredBy;
    private UUID studentA;
    private UUID studentB;
    private UUID studentC;
    private UUID studentCrossSchool;
    private UUID projectId;
    private UUID ruleVersionId;
    private UUID otherRuleVersionId;
    private UUID activityProjectA1;
    private UUID activityProjectA2;
    private UUID activityProjectOutside;
    private UUID activityProjectSchoolB;
    private UUID activityProjectOtherRule;
    private UsernamePasswordAuthenticationToken adminAuthentication;

    @BeforeEach
    void setUp() {
        schoolA = UUID.randomUUID();
        schoolB = UUID.randomUUID();
        adminA = UUID.randomUUID();
        enteredBy = UUID.randomUUID();
        studentA = UUID.fromString("00000000-0000-0000-0000-000000000141");
        studentB = UUID.fromString("00000000-0000-0000-0000-000000000142");
        studentC = UUID.fromString("00000000-0000-0000-0000-000000000143");
        studentCrossSchool = UUID.fromString("00000000-0000-0000-0000-000000000144");
        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        otherRuleVersionId = UUID.randomUUID();
        activityProjectA1 = UUID.randomUUID();
        activityProjectA2 = UUID.randomUUID();
        activityProjectOutside = UUID.randomUUID();
        activityProjectSchoolB = UUID.randomUUID();
        activityProjectOtherRule = UUID.randomUUID();

        insertSchool(schoolA, "school-a");
        insertSchool(schoolB, "school-b");
        insertUser(adminA, "admin-a");
        insertUser(enteredBy, "entered-by");
        insertUser(studentA, "student-a");
        insertUser(studentB, "student-b");
        insertUser(studentC, "student-c");
        insertUser(studentCrossSchool, "student-cross");
        insertMembership(UUID.randomUUID(), adminA, schoolA, "SCHOOL_ADMIN", null, null);
        insertMembership(UUID.randomUUID(), studentA, schoolA, "STUDENT", "G5", "C1");
        insertMembership(UUID.randomUUID(), studentB, schoolA, "STUDENT", "G5", "C1");
        insertMembership(UUID.randomUUID(), studentC, schoolA, "STUDENT", "G6", "C2");
        insertMembership(UUID.randomUUID(), studentCrossSchool, schoolB, "STUDENT", "G5", "C1");
        insertProject(projectId, ruleVersionId, otherRuleVersionId, "INTEGER", "HIGHER_BETTER", "pts");
        activityProjectA1 = insertActivityProject(activityProjectA1, schoolA, ruleVersionId,
                "2026-01-05T00:00:00Z", "2026-01-05T02:00:00Z");
        activityProjectA2 = insertActivityProject(activityProjectA2, schoolA, ruleVersionId,
                "2026-01-10T00:00:00Z", "2026-01-10T02:00:00Z");
        activityProjectOutside = insertActivityProject(activityProjectOutside, schoolA, ruleVersionId,
                "2026-02-10T00:00:00Z", "2026-02-10T02:00:00Z");
        activityProjectSchoolB = insertActivityProject(activityProjectSchoolB, schoolB, ruleVersionId,
                "2026-01-06T00:00:00Z", "2026-01-06T02:00:00Z");
        activityProjectOtherRule = insertActivityProject(activityProjectOtherRule, schoolA, otherRuleVersionId,
                "2026-01-07T00:00:00Z", "2026-01-07T02:00:00Z");
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
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = null WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM project_rule_versions WHERE project_id IN (SELECT id FROM challenge_projects WHERE name LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM challenge_projects WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("""
                DELETE FROM student_profiles
                WHERE membership_id IN (
                    SELECT id FROM school_memberships
                    WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)
                )
                """, runPrefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    @Test
    void l2GenerationSelectsBestCurrentEffectiveScoreAcrossActivitiesAndSnapshotsSource() {
        UUID selectedA = insertScore(activityProjectA2, studentA, schoolA, 1, "APPROVED", true, "95");
        insertScore(activityProjectA1, studentA, schoolA, 1, "APPROVED", true, "90");
        UUID selectedB = insertScore(activityProjectA1, studentB, schoolA, 1, "APPROVED", true, "88");
        insertScore(activityProjectA1, studentC, schoolA, 1, "APPROVED", true, "99");
        insertScore(activityProjectOutside, studentA, schoolA, 2, "APPROVED", true, "100");
        insertScore(activityProjectSchoolB, studentCrossSchool, schoolB, 1, "APPROVED", true, "100");
        insertScore(activityProjectA1, studentB, schoolA, 2, "APPROVED", false, "120");
        insertScore(activityProjectA2, studentB, schoolA, 2, "REJECTED", false, "130");

        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L2,
                runPrefix + "-l2-ranking",
                schoolA,
                projectId,
                null,
                """
                        {
                          "grade": "G5",
                          "className": "C1",
                          "activityPeriodStart": "2026-01-01T00:00:00Z",
                          "activityPeriodEnd": "2026-01-31T23:59:59Z"
                        }
                        """).id();

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.entryCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?", UUID.class, definitionId))
                .isNull();
        assertThat(jdbc.queryForObject("""
                SELECT data_scope_snapshot ->> 'selectionPolicy'
                FROM ranking_versions
                WHERE id = ?
                """, String.class, result.rankingVersionId())).isEqualTo("BEST_SCORE");

        List<String> scores = jdbc.queryForList("""
                SELECT score_display_value
                FROM ranking_entries
                WHERE version_id = ?
                ORDER BY rank_position ASC, student_id ASC
                """, String.class, result.rankingVersionId());
        assertThat(scores).containsExactly("95", "88");
        assertThat(sourceFor(definitionId, studentA)).isEqualTo(selectedA);
        assertThat(sourceFor(definitionId, studentB)).isEqualTo(selectedB);
    }

    @Test
    void l2GenerationRejectsCandidatesFromMultipleRuleVersions() {
        insertScore(activityProjectA1, studentA, schoolA, 1, "APPROVED", true, "90");
        insertScore(activityProjectOtherRule, studentB, schoolA, 1, "APPROVED", true, "95");
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L2,
                runPrefix + "-l2-rule-conflict",
                schoolA,
                projectId,
                null,
                null).id();

        assertThatThrownBy(() -> rankingGeneration.generate(definitionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple RuleVersions");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ?",
                Integer.class, definitionId)).isZero();
    }

    @Test
    void l2EmptyRankingCreatesGeneratedVersion() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L2,
                runPrefix + "-l2-empty",
                schoolA,
                projectId,
                null,
                "{\"grade\":\"NO_MATCH\"}").id();

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.entryCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_entries WHERE version_id = ?",
                Integer.class, result.rankingVersionId())).isZero();
    }

    @Test
    void l2DefinitionUniquenessIsEnforcedPerSchoolAndProject() {
        rankingDefinitions.create(RankingLayer.L2, runPrefix + "-l2-unique-a", schoolA, projectId, null, null);

        assertThatThrownBy(() -> rankingDefinitions.create(
                RankingLayer.L2, runPrefix + "-l2-unique-b", schoolA, projectId, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schoolAdminCannotGenerateOtherSchoolL2Definition() {
        UUID definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ranking_definitions(id, layer, name, school_id, project_id, dimension_filters, created_by)
                VALUES (?, 'L2', ?, ?, ?, ?::jsonb, ?)
                """, definitionId, runPrefix + "-l2-other-school", schoolB, projectId,
                "{\"selectionPolicy\":\"BEST_SCORE\"}", adminA);

        assertThatThrownBy(() -> rankingGeneration.generate(definitionId))
                .isInstanceOf(IdentityApplicationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ?",
                Integer.class, definitionId)).isZero();
    }

    @Test
    void concurrentL2GenerationAllocatesDistinctVersionNumbers() throws Exception {
        insertScore(activityProjectA1, studentA, schoolA, 1, "APPROVED", true, "90");
        insertScore(activityProjectA2, studentA, schoolA, 1, "APPROVED", true, "95");
        insertScore(activityProjectA1, studentB, schoolA, 1, "APPROVED", true, "88");
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L2,
                runPrefix + "-l2-concurrent",
                schoolA,
                projectId,
                null,
                null).id();

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
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ?",
                    Integer.class, definitionId)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID sourceFor(UUID definitionId, UUID studentId) {
        return jdbc.queryForObject("""
                SELECT res.score_attempt_id
                FROM ranking_entry_score_sources res
                JOIN ranking_entries re ON re.id = res.entry_id
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ? AND re.student_id = ?
                """, UUID.class, definitionId, studentId);
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

    private com.campusguinness.ranking.application.result.RankingGenerationResult runConcurrent(
            CountDownLatch ready,
            CountDownLatch start,
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

    private void insertMembership(UUID membershipId, UUID userId, UUID schoolId, String role, String grade, String className) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, membershipId, userId, schoolId, role);
        if ("STUDENT".equals(role)) {
            jdbc.update("""
                    INSERT INTO student_profiles(id, membership_id, grade, class_name, student_number)
                    VALUES (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), membershipId, grade, className, runPrefix + "-num");
        }
    }

    private void insertProject(UUID projectId, UUID ruleVersionId, UUID otherRuleVersionId,
                               String storageType, String direction, String unit) {
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id, name, category, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """,
                projectId, runPrefix + "-project", "SPORTS", storageType, "NUMERIC",
                direction, unit, "BEST", "PUBLISHED");
        insertRuleVersion(ruleVersionId, projectId, 1, storageType, direction, unit);
        insertRuleVersion(otherRuleVersionId, projectId, 2, storageType, direction, unit);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = ? WHERE id = ?", ruleVersionId, projectId);
    }

    private void insertRuleVersion(UUID ruleVersionId, UUID projectId, int versionNumber,
                                   String storageType, String direction, String unit) {
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id, project_id, version_number, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, rules_text, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                ruleVersionId, projectId, versionNumber, storageType, "NUMERIC", direction, unit, "BEST",
                runPrefix + "-rules-" + versionNumber, enteredBy);
    }

    private UUID insertActivityProject(UUID activityProjectId, UUID schoolId, UUID ruleVersionId, String start, String end) {
        UUID activityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(
                    id, school_id, title, start_time, end_time, execution_status, public_status, created_by
                ) VALUES (?,?,?,?,?,?,?,?)
                """, activityId, schoolId, runPrefix + "-activity", Timestamp.from(Instant.parse(start)),
                Timestamp.from(Instant.parse(end)), "PUBLISHED", "PUBLIC", enteredBy);
        jdbc.update("INSERT INTO activity_projects(id, activity_id, project_id, rule_version_id) VALUES (?,?,?,?)",
                activityProjectId, activityId, projectId, ruleVersionId);
        return activityProjectId;
    }

    private UUID insertScore(UUID activityProjectId, UUID studentId, UUID schoolId, int attemptNumber,
                             String status, boolean currentEffective, String scoreValue) {
        UUID scoreAttemptId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO score_attempts(
                    id, school_id, activity_project_id, student_id, attempt_number,
                    score_storage_type, score_value, is_current_effective, score_status,
                    entered_by, score_business_time, version
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                scoreAttemptId, schoolId, activityProjectId, studentId, attemptNumber,
                "INTEGER", new BigDecimal(scoreValue), currentEffective, status, enteredBy,
                Timestamp.from(Instant.now()), 1);
        return scoreAttemptId;
    }
}
