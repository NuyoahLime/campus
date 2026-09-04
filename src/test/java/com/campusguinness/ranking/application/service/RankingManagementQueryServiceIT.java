package com.campusguinness.ranking.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingManagementQueryServiceIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private RankingDefinitionApplicationService rankingDefinitions;
    @Autowired private RankingGenerationApplicationService rankingGeneration;
    @Autowired private RankingPublicationApplicationService rankingPublication;
    @Autowired private RankingManagementQueryService managementQuery;
    @Autowired private RankingReadQueryService rankingRead;
    @Autowired private JdbcTemplate jdbc;

    private final String runPrefix = "p3mgmt-" + UUID.randomUUID().toString().substring(0, 8);
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
    void sameSchoolAdminCanPreviewGeneratedSnapshotAndRefreshPublishedState() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1, runPrefix + "-ranking", null, projectId, activityProjectId).id();
        insertScore(studentA, schoolA, 1, "98");
        insertScore(studentB, schoolA, 1, "95");

        var generated = rankingGeneration.generate(definitionId);
        assertThatThrownBy(() -> rankingRead.publicDetail(definitionId))
                .isInstanceOf(IllegalArgumentException.class);

        var page = managementQuery.list(0, 20);
        assertThat(page.items()).extracting("id").contains(definitionId);

        var detail = managementQuery.detail(definitionId);
        assertThat(detail.enabled()).isTrue();
        assertThat(detail.schoolId()).isEqualTo(schoolA);
        assertThat(detail.activityProjectId()).isEqualTo(activityProjectId);
        assertThat(detail.latestGeneratedVersion().id()).isEqualTo(generated.rankingVersionId());
        assertThat(detail.latestGeneratedVersion().status()).isEqualTo("GENERATED");
        assertThat(detail.latestGeneratedVersion().entryCount()).isEqualTo(2);
        assertThat(detail.latestGeneratedVersion().entries()).hasSize(2);
        assertThat(detail.currentPublishedVersion()).isNull();

        rankingPublication.publish(definitionId, generated.rankingVersionId());

        var refreshed = managementQuery.detail(definitionId);
        assertThat(refreshed.latestGeneratedVersion()).isNull();
        assertThat(refreshed.currentPublishedVersion().id()).isEqualTo(generated.rankingVersionId());
        assertThat(refreshed.currentPublishedVersion().status()).isEqualTo("PUBLISHED");
        assertThat(rankingRead.schoolAdminDetail(definitionId).versionNumber()).isEqualTo(generated.versionNumber());
        assertThat(rankingRead.publicDetail(definitionId).entries()).hasSize(2);
    }

    @Test
    void sameSchoolAdminCanReadL2ManagementDefinition() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L2,
                runPrefix + "-l2-ranking",
                null,
                projectId,
                null,
                """
                {"selectionPolicy":"BEST_SCORE","grade":"G5","className":"C1","activityPeriodStart":"2026-01-01T00:00:00Z","activityPeriodEnd":"2026-01-31T23:59:59Z"}
                """).id();

        var page = managementQuery.list(0, 20);
        assertThat(page.items()).extracting("id").contains(definitionId);

        var detail = managementQuery.detail(definitionId);
        assertThat(detail.layer()).isEqualTo("L2");
        assertThat(detail.schoolId()).isEqualTo(schoolA);
        assertThat(detail.projectId()).isEqualTo(projectId);
        assertThat(detail.activityId()).isNull();
        assertThat(detail.activityProjectId()).isNull();
        assertThat(detail.selectionPolicy()).isEqualTo("BEST_SCORE");
        assertThat(detail.grade()).isEqualTo("G5");
        assertThat(detail.className()).isEqualTo("C1");
        assertThat(detail.activityPeriodStart()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(detail.activityPeriodEnd()).isEqualTo(Instant.parse("2026-01-31T23:59:59Z"));
        assertThat(detail.currentPublishedVersion()).isNull();
        assertThat(detail.latestGeneratedVersion()).isNull();
    }

    @Test
    void managementReadIsSameSchoolOnly() {
        UUID definitionId = rankingDefinitions.create(
                RankingLayer.L1, runPrefix + "-school-a-ranking", null, projectId, activityProjectId).id();

        authenticateSchoolAdmin(adminB, schoolB);

        assertThat(managementQuery.list(0, 20).items()).extracting("id").doesNotContain(definitionId);
        assertThatThrownBy(() -> managementQuery.detail(definitionId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void authenticateSchoolAdmin(UUID userId, UUID schoolId) {
        var details = new CampusGuinnessUserDetails(
                userId,
                runPrefix + "-admin",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")),
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, "SCHOOL_ADMIN")));
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

    private void insertScore(UUID studentId, UUID schoolId, int attemptNumber, String scoreValue) {
        jdbc.update("""
                INSERT INTO score_attempts(
                    id, school_id, activity_project_id, student_id, attempt_number,
                    score_storage_type, score_value, is_current_effective,
                    score_status, entered_by, score_business_time, version
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), schoolId, activityProjectId, studentId, attemptNumber,
                "INTEGER", new BigDecimal(scoreValue), true, "APPROVED", enteredBy, Timestamp.from(Instant.now()), 1);
    }
}
