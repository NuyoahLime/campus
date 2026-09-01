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

class RankingPublicationApplicationServiceIT extends PostgreSqlIntegrationTestSupport {

    @Autowired private RankingDefinitionApplicationService rankingDefinitions;
    @Autowired private RankingGenerationApplicationService rankingGeneration;
    @Autowired private RankingPublicationApplicationService rankingPublication;
    @Autowired private RankingReadQueryService rankingRead;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private final String runPrefix = "p2pub-" + UUID.randomUUID().toString().substring(0, 6);
    private UUID schoolA;
    private UUID schoolB;
    private UUID adminA;
    private UUID adminB;
    private UUID studentA;
    private UUID studentB;
    private UUID enteredBy;
    private UUID projectId;
    private UUID ruleVersionId;
    private UUID activityId;
    private UUID activityProjectId;
    private UsernamePasswordAuthenticationToken adminAAuth;

    @BeforeEach
    void setUp() {
        schoolA = UUID.randomUUID();
        schoolB = UUID.randomUUID();
        adminA = UUID.randomUUID();
        adminB = UUID.randomUUID();
        studentA = UUID.randomUUID();
        studentB = UUID.randomUUID();
        enteredBy = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        activityProjectId = UUID.randomUUID();

        insertSchool(schoolA, "school-a");
        insertSchool(schoolB, "school-b");
        insertUser(adminA, "admin-a");
        insertUser(adminB, "admin-b");
        insertUser(studentA, "student-a");
        insertUser(studentB, "student-b");
        insertUser(enteredBy, "entered-by");
        insertMembership(adminA, schoolA, "SCHOOL_ADMIN");
        insertMembership(adminB, schoolB, "SCHOOL_ADMIN");
        insertMembership(studentA, schoolA, "STUDENT");
        insertMembership(studentB, schoolA, "STUDENT");
        insertProject(projectId, ruleVersionId);
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
    void generatedL1PublishesAndExistingReadSideExposesSnapshot() {
        UUID definitionId = createDefinition("-ranking");
        UUID sourceA = insertScore(studentA, schoolA, 1, "98");
        insertScore(studentB, schoolA, 1, "95");
        var generated = rankingGeneration.generate(definitionId);
        SnapshotCounts before = counts(generated.rankingVersionId());

        assertThatThrownBy(() -> rankingRead.publicDetail(definitionId))
                .isInstanceOf(IllegalArgumentException.class);

        var published = rankingPublication.publish(definitionId, generated.rankingVersionId());

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.previousCurrentVersionId()).isNull();
        assertThat(published.currentVersionId()).isEqualTo(generated.rankingVersionId());
        assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?", String.class,
                generated.rankingVersionId())).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT published_at FROM ranking_versions WHERE id = ?", Timestamp.class,
                generated.rankingVersionId())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?", UUID.class,
                definitionId)).isEqualTo(generated.rankingVersionId());
        assertThat(counts(generated.rankingVersionId())).isEqualTo(before);

        var publicRead = rankingRead.publicDetail(definitionId);
        assertThat(publicRead.versionNumber()).isEqualTo(1);
        assertThat(publicRead.entries()).extracting(e -> e.scoreDisplayValue()).containsExactly("98", "95");
        authenticateStudent(studentA, schoolA);
        assertThat(rankingRead.studentDetail(definitionId).versionNumber()).isEqualTo(1);
        authenticateSchoolAdmin(adminA, schoolA);
        assertThat(rankingRead.schoolAdminDetail(definitionId).versionNumber()).isEqualTo(1);
        assertThat(sourceFor(generated.rankingVersionId(), studentA)).isEqualTo(sourceA);
    }

    @Test
    void publishingNewGeneratedVersionReplacesOldCurrentWithoutMutatingEntriesOrSources() {
        UUID definitionId = createDefinition("-replacement");
        UUID oldScore = insertScore(studentA, schoolA, 1, "98");
        var v1 = rankingGeneration.generate(definitionId);
        rankingPublication.publish(definitionId, v1.rankingVersionId());
        SnapshotCounts v1Counts = counts(v1.rankingVersionId());
        jdbc.update("UPDATE score_attempts SET is_current_effective = false WHERE id = ?", oldScore);
        UUID newScore = insertScore(studentA, schoolA, 2, "99");
        var v2 = rankingGeneration.generate(definitionId);

        rankingPublication.publish(definitionId, v2.rankingVersionId());

        assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?", String.class,
                v1.rankingVersionId())).isEqualTo("REPLACED");
        assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?", String.class,
                v2.rankingVersionId())).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?", UUID.class,
                definitionId)).isEqualTo(v2.rankingVersionId());
        assertThat(counts(v1.rankingVersionId())).isEqualTo(v1Counts);
        assertThat(sourceFor(v1.rankingVersionId(), studentA)).isEqualTo(oldScore);
        assertThat(sourceFor(v2.rankingVersionId(), studentA)).isEqualTo(newScore);
        assertThat(rankingRead.publicDetail(definitionId).entries().getFirst().scoreDisplayValue()).isEqualTo("99");
    }

    @Test
    void onlyGeneratedSameDefinitionVersionCanPublish() {
        UUID definitionA = createDefinition("-definition-a");
        UUID definitionB = createDefinition("-definition-b");
        UUID generatedA = rankingGeneration.generate(definitionA).rankingVersionId();
        UUID generatedB = rankingGeneration.generate(definitionB).rankingVersionId();
        assertThatThrownBy(() -> rankingPublication.publish(definitionA, generatedB))
                .isInstanceOf(IllegalStateException.class);

        for (String status : List.of("DRAFT_CALC", "PUBLISHED", "REPLACED", "WITHDRAWN", "EXPIRED", "VOIDED")) {
            UUID versionId = insertVersion(definitionA, status);
            assertThatThrownBy(() -> rankingPublication.publish(definitionA, versionId))
                    .as(status)
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?", String.class,
                generatedA)).isEqualTo("GENERATED");
    }

    @Test
    void currentPointerToGeneratedTargetIsRejectedAsInconsistentState() {
        UUID definitionId = createDefinition("-generated-current");
        UUID generated = rankingGeneration.generate(definitionId).rankingVersionId();
        jdbc.update("UPDATE ranking_definitions SET current_version_id = ? WHERE id = ?", generated, definitionId);

        assertThatThrownBy(() -> rankingPublication.publish(definitionId, generated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current version state is inconsistent");

        assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?", String.class,
                generated)).isEqualTo("GENERATED");
        assertThat(jdbc.queryForObject("SELECT published_at FROM ranking_versions WHERE id = ?", Timestamp.class,
                generated)).isNull();
    }

    @Test
    void authorizationAndDefinitionStateAreEnforced() {
        UUID definitionId = createDefinition("-auth");
        UUID generated = rankingGeneration.generate(definitionId).rankingVersionId();

        authenticateSchoolAdmin(adminB, schoolB);
        assertThatThrownBy(() -> rankingPublication.publish(definitionId, generated))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo("SCHOOL_ADMIN_SCOPE_DENIED"));

        authenticateStudent(studentA, schoolA);
        assertThatThrownBy(() -> rankingPublication.publish(definitionId, generated))
                .isInstanceOfSatisfying(IdentityApplicationException.class,
                        ex -> assertThat(ex.code()).isEqualTo("SCHOOL_ADMIN_SCOPE_DENIED"));

        authenticateSchoolAdmin(adminA, schoolA);
        rankingDefinitions.disable(definitionId);
        assertThatThrownBy(() -> rankingPublication.publish(definitionId, generated))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonL1DefinitionCannotPublish() {
        UUID definitionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ranking_definitions(id, layer, name, school_id, project_id, created_by)
                VALUES (?, 'L2', ?, ?, ?, ?)
                """, definitionId, runPrefix + "-l2", schoolA, projectId, adminA);
        UUID versionId = insertVersion(definitionId, "GENERATED");

        assertThatThrownBy(() -> rankingPublication.publish(definitionId, versionId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyGeneratedRankingCanPublish() {
        UUID definitionId = createDefinition("-empty");
        var generated = rankingGeneration.generate(definitionId);

        rankingPublication.publish(definitionId, generated.rankingVersionId());

        assertThat(rankingRead.publicDetail(definitionId).entries()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?", String.class,
                generated.rankingVersionId())).isEqualTo("PUBLISHED");
    }

    @Test
    void concurrentPublicationSerializesOnDefinitionLock() throws Exception {
        UUID definitionId = createDefinition("-concurrent");
        insertScore(studentA, schoolA, 1, "98");
        var v1 = rankingGeneration.generate(definitionId);
        jdbc.update("UPDATE score_attempts SET is_current_effective = false WHERE student_id = ?", studentA);
        insertScore(studentA, schoolA, 2, "99");
        var v2 = rankingGeneration.generate(definitionId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> publishConcurrently(definitionId, v1.rankingVersionId(), ready, start));
            Future<?> second = executor.submit(() -> publishConcurrently(definitionId, v2.rankingVersionId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            UUID current = jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?",
                    UUID.class, definitionId);
            assertThat(current).isIn(v1.rankingVersionId(), v2.rankingVersionId());
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ranking_versions
                    WHERE definition_id = ? AND version_status = 'PUBLISHED'
                    """, Integer.class, definitionId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ranking_versions
                    WHERE definition_id = ? AND version_status IN ('PUBLISHED', 'REPLACED')
                    """, Integer.class, definitionId)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void publicationWaitsForConcurrentDisableAndThenRejects() throws Exception {
        UUID definitionId = createDefinition("-disable-race");
        insertScore(studentA, schoolA, 1, "98");
        UUID versionId = rankingGeneration.generate(definitionId).rankingVersionId();
        CountDownLatch disableHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseDisable = new CountDownLatch(1);
        CountDownLatch publicationFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> disabling = executor.submit(() -> {
                new TransactionTemplate(txManager).executeWithoutResult(status -> {
                    jdbc.queryForObject("SELECT id FROM ranking_definitions WHERE id = ? FOR UPDATE",
                            UUID.class, definitionId);
                    jdbc.update("UPDATE ranking_definitions SET is_enabled = false WHERE id = ?", definitionId);
                    disableHoldingLock.countDown();
                    await(releaseDisable);
                });
                return null;
            });
            assertThat(disableHoldingLock.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> publication = executor.submit(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(adminAAuth);
                    rankingPublication.publish(definitionId, versionId);
                    return null;
                } finally {
                    SecurityContextHolder.clearContext();
                    publicationFinished.countDown();
                }
            });
            assertThat(publicationFinished.await(300, TimeUnit.MILLISECONDS)).isFalse();
            releaseDisable.countDown();

            assertThatThrownBy(() -> publication.get(30, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
            disabling.get(30, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?",
                    String.class, versionId)).isEqualTo("GENERATED");
            assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?",
                    UUID.class, definitionId)).isNull();
        } finally {
            releaseDisable.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void generationAndPublicationSerializeOnDefinitionLock() throws Exception {
        UUID definitionId = createDefinition("-generate-publish");
        insertScore(studentA, schoolA, 1, "98");
        UUID v1 = rankingGeneration.generate(definitionId).rankingVersionId();
        jdbc.update("UPDATE score_attempts SET is_current_effective = false WHERE student_id = ?", studentA);
        insertScore(studentA, schoolA, 2, "99");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> publish = executor.submit(() -> publishConcurrently(definitionId, v1, ready, start));
            Future<?> generate = executor.submit(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(adminAAuth);
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    rankingGeneration.generate(definitionId);
                    return null;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            publish.get(30, TimeUnit.SECONDS);
            generate.get(30, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?",
                    UUID.class, definitionId)).isEqualTo(v1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ?",
                    Integer.class, definitionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ranking_versions WHERE definition_id = ? AND version_status = 'GENERATED'",
                    Integer.class, definitionId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void publicationRollsBackWhenOuterTransactionFails() {
        UUID definitionId = createDefinition("-rollback");
        insertScore(studentA, schoolA, 1, "98");
        UUID versionId = rankingGeneration.generate(definitionId).rankingVersionId();
        SnapshotCounts before = counts(versionId);

        TransactionTemplate tt = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tt.executeWithoutResult(status -> {
            rankingPublication.publish(definitionId, versionId);
            throw new RuntimeException("simulated failure after publication");
        })).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT version_status FROM ranking_versions WHERE id = ?", String.class,
                versionId)).isEqualTo("GENERATED");
        assertThat(jdbc.queryForObject("SELECT published_at FROM ranking_versions WHERE id = ?", Timestamp.class,
                versionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT current_version_id FROM ranking_definitions WHERE id = ?",
                UUID.class, definitionId)).isNull();
        assertThat(counts(versionId)).isEqualTo(before);
    }

    private UUID createDefinition(String suffix) {
        return rankingDefinitions.create(
                RankingLayer.L1,
                runPrefix + suffix,
                schoolA,
                projectId,
                activityProjectId).id();
    }

    private UUID insertVersion(UUID definitionId, String status) {
        Integer maxVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) FROM ranking_versions WHERE definition_id = ?",
                Integer.class, definitionId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ranking_versions(
                    id, definition_id, version_number, version_status, generated_at, published_at, created_reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'TEST', ?)
                """,
                versionId,
                definitionId,
                maxVersion == null ? 1 : maxVersion + 1,
                status,
                Timestamp.from(Instant.now()),
                "PUBLISHED".equals(status) || "REPLACED".equals(status) ? Timestamp.from(Instant.now()) : null,
                Timestamp.from(Instant.now()));
        return versionId;
    }

    private void publishConcurrently(UUID definitionId, UUID versionId, CountDownLatch ready, CountDownLatch start) {
        try {
            SecurityContextHolder.getContext().setAuthentication(adminAAuth);
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            rankingPublication.publish(definitionId, versionId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private SnapshotCounts counts(UUID versionId) {
        Integer entries = jdbc.queryForObject("SELECT COUNT(*) FROM ranking_entries WHERE version_id = ?",
                Integer.class, versionId);
        Integer sources = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM ranking_entry_score_sources s
                JOIN ranking_entries e ON e.id = s.entry_id
                WHERE e.version_id = ?
                """, Integer.class, versionId);
        return new SnapshotCounts(entries == null ? 0 : entries, sources == null ? 0 : sources);
    }

    private UUID sourceFor(UUID versionId, UUID studentId) {
        return jdbc.queryForObject("""
                SELECT s.score_attempt_id
                FROM ranking_entry_score_sources s
                JOIN ranking_entries e ON e.id = s.entry_id
                WHERE e.version_id = ? AND e.student_id = ?
                """, UUID.class, versionId, studentId);
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private void authenticateSchoolAdmin(UUID userId, UUID schoolId) {
        var details = new CampusGuinnessUserDetails(
                userId,
                runPrefix + "-admin",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, "SCHOOL_ADMIN")));
        adminAAuth = new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(adminAAuth);
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

    private void insertMembership(UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, schoolId, role);
    }

    private void insertProject(UUID projectId, UUID ruleVersionId) {
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id, name, category, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """,
                projectId, runPrefix + "-project", "SPORTS", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "pts", "BEST", "PUBLISHED");
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id, project_id, version_number, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, rules_text, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER", "pts", "BEST",
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

    private UUID insertScore(UUID studentId, UUID schoolId, int attemptNumber, String scoreValue) {
        UUID scoreAttemptId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO score_attempts(
                    id, school_id, activity_project_id, student_id, attempt_number,
                    score_storage_type, score_value, is_current_effective,
                    score_status, entered_by, score_business_time, version
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                scoreAttemptId, schoolId, activityProjectId, studentId, attemptNumber,
                "INTEGER", new BigDecimal(scoreValue), true, "APPROVED", enteredBy, Timestamp.from(Instant.now()), 1);
        return scoreAttemptId;
    }

    private record SnapshotCounts(int entries, int sources) {}
}
