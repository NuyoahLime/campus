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

class L3RankingGenerationApplicationServiceIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private L3RankingDefinitionApplicationService rankingDefinitions;
    @Autowired private RankingGenerationApplicationService rankingGeneration;
    @Autowired private JdbcTemplate jdbc;

    private final String runPrefix = "l3-gen-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolId;
    private UUID projectId;
    private UUID ruleVersionId;
    private UUID adminId;
    private UUID superAdminId;
    private UUID adminMembershipId;
    private UUID studentBlankId;
    private UUID studentBlankMembershipId;
    private UUID studentGradeNullId;
    private UUID studentGradeNullMembershipId;
    private UUID studentClassNullId;
    private UUID studentClassNullMembershipId;
    private UUID studentWinnerId;
    private UUID studentWinnerMembershipId;
    private UUID activityBlankId;
    private UUID activityBlankProjectId;
    private UUID activityGradeNullId;
    private UUID activityGradeNullProjectId;
    private UUID activityClassNullId;
    private UUID activityClassNullProjectId;
    private UUID activityWinnerAId;
    private UUID activityWinnerAProjectId;
    private UUID activityWinnerBId;
    private UUID activityWinnerBProjectId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        superAdminId = UUID.randomUUID();
        studentBlankId = UUID.randomUUID();
        studentGradeNullId = UUID.randomUUID();
        studentClassNullId = UUID.randomUUID();
        studentWinnerId = UUID.randomUUID();
        activityBlankId = UUID.randomUUID();
        activityGradeNullId = UUID.randomUUID();
        activityClassNullId = UUID.randomUUID();
        activityWinnerAId = UUID.randomUUID();
        activityWinnerBId = UUID.randomUUID();

        insertSchool(schoolId, "school-a");
        insertUser(adminId, "admin-a", null);
        insertUser(superAdminId, "super-admin", "SUPER_ADMIN");
        insertUser(studentBlankId, "student-blank", null);
        insertUser(studentGradeNullId, "student-grade-null", null);
        insertUser(studentClassNullId, "student-class-null", null);
        insertUser(studentWinnerId, "student-winner", null);
        adminMembershipId = UUID.randomUUID();
        studentBlankMembershipId = UUID.randomUUID();
        studentGradeNullMembershipId = UUID.randomUUID();
        studentClassNullMembershipId = UUID.randomUUID();
        studentWinnerMembershipId = UUID.randomUUID();
        insertMembership(adminMembershipId, adminId, schoolId, "SCHOOL_ADMIN");
        insertMembership(studentBlankMembershipId, studentBlankId, schoolId, "STUDENT");
        insertMembership(studentGradeNullMembershipId, studentGradeNullId, schoolId, "STUDENT");
        insertMembership(studentClassNullMembershipId, studentClassNullId, schoolId, "STUDENT");
        insertMembership(studentWinnerMembershipId, studentWinnerId, schoolId, "STUDENT");
        insertStudentProfile(UUID.randomUUID(), studentGradeNullMembershipId, null, "C1");
        insertStudentProfile(UUID.randomUUID(), studentClassNullMembershipId, "G5", null);
        insertStudentProfile(UUID.randomUUID(), studentWinnerMembershipId, "G5", "C1");
        insertProject(projectId, ruleVersionId);
        activityBlankId = insertActivity("activity-blank", null, null);
        activityGradeNullId = insertActivity("activity-grade-null", null, null);
        activityClassNullId = insertActivity("activity-class-null", null, null);
        activityBlankProjectId = insertActivityProject(activityBlankId);
        activityGradeNullProjectId = insertActivityProject(activityGradeNullId);
        activityClassNullProjectId = insertActivityProject(activityClassNullId);
        authenticateSuperAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        String rankingPattern = "%" + runPrefix + "%";
        jdbc.update("UPDATE ranking_definitions SET current_version_id = null WHERE name LIKE ?", rankingPattern);
        jdbc.update("""
                DELETE FROM ranking_entry_score_sources
                WHERE score_attempt_id IN (
                    SELECT id FROM score_attempts
                    WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)
                )
                """, rankingPattern);
        jdbc.update("""
                DELETE FROM ranking_entries
                WHERE version_id IN (
                    SELECT id FROM ranking_versions
                    WHERE definition_id IN (
                        SELECT id FROM ranking_definitions WHERE name LIKE ?
                    )
                )
                """, rankingPattern);
        jdbc.update("DELETE FROM ranking_versions WHERE definition_id IN (SELECT id FROM ranking_definitions WHERE name LIKE ?)", rankingPattern);
        jdbc.update("DELETE FROM ranking_definitions WHERE name LIKE ?", rankingPattern);
        jdbc.update("DELETE FROM l3_authorizations WHERE school_id IN (SELECT id FROM schools WHERE name LIKE ?)", runPrefix + "%");
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
    void generationAllowsBlankScopeAndStudentsWithoutProfiles() {
        UUID definitionId = createDefinition();
        UUID authId = insertApprovedAuthorization("{}",
                false, false);
        UUID scoreAttemptId = insertScore(activityBlankProjectId, studentBlankId, 86, true, "2026-01-01T00:00:00Z");

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.entryCount()).isEqualTo(1);
        assertThat(entryCount(result.rankingVersionId())).isEqualTo(1);
        assertThat(studentDisplayName(result.rankingVersionId())).isEqualTo("匿名选手");
        assertThat(schoolName(result.rankingVersionId())).isNull();
        assertThat(scoreSource(result.rankingVersionId())).isEqualTo(scoreAttemptId);
        assertThat(authorizationIdsSnapshot(result.rankingVersionId())).contains(authId.toString());
    }

    @Test
    void generationExcludesMissingGradeWhenGradeFilterApplies() {
        UUID definitionId = createDefinition();
        insertApprovedAuthorization("{\"grades\":[\"G5\"]}", false, false);
        insertScore(activityGradeNullProjectId, studentGradeNullId, 71, true, "2026-01-02T00:00:00Z");

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.entryCount()).isZero();
        assertThat(entryCount(result.rankingVersionId())).isZero();
    }

    @Test
    void generationExcludesMissingClassWhenClassFilterApplies() {
        UUID definitionId = createDefinition();
        insertApprovedAuthorization("{\"classNames\":[\"C1\"]}", false, false);
        insertScore(activityClassNullProjectId, studentClassNullId, 72, true, "2026-01-03T00:00:00Z");

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.entryCount()).isZero();
        assertThat(entryCount(result.rankingVersionId())).isZero();
    }

    @Test
    void generationAllowsNullActivityTimesWithoutPeriodFilters() {
        UUID definitionId = createDefinition();
        UUID authId = insertApprovedAuthorization("{\"activityIds\":[\"" + activityBlankId + "\"]}",
                false, false);
        insertScore(activityBlankProjectId, studentBlankId, 88, true, "2026-01-04T00:00:00Z");

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.entryCount()).isEqualTo(1);
        assertThat(authorizationIdsSnapshot(result.rankingVersionId())).contains(authId.toString());
    }

    @Test
    void generationExcludesNullActivityTimesWhenPeriodFiltersApply() {
        UUID definitionId = createDefinition();
        insertApprovedAuthorization("{\"activityPeriodStart\":\"2026-01-01T00:00:00Z\"}", false, false);
        insertApprovedAuthorization("{\"activityPeriodEnd\":\"2026-01-31T00:00:00Z\"}", false, false);
        insertScore(activityBlankProjectId, studentBlankId, 89, true, "2026-01-05T00:00:00Z");

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.entryCount()).isZero();
        assertThat(entryCount(result.rankingVersionId())).isZero();
    }

    @Test
    void generationUsesWinningAuthorizationOnlyAndHashesPublicStudentName() {
        UUID definitionId = createDefinition();
        UUID activityA = insertActivity("activity-a", null, null);
        UUID activityB = insertActivity("activity-b", null, null);
        activityWinnerAProjectId = insertActivityProject(activityA);
        activityWinnerBProjectId = insertActivityProject(activityB);
        UUID authA = insertApprovedAuthorization("{\"activityIds\":[\"" + activityA + "\"]}", false, true);
        UUID authB = insertApprovedAuthorization("{\"activityIds\":[\"" + activityB + "\"]}", false, true);
        UUID lowScore = insertScore(activityWinnerAProjectId, studentWinnerId, 80, true, "2026-01-06T00:00:00Z");
        UUID highScore = insertScore(activityWinnerBProjectId, studentWinnerId, 95, true, "2026-01-07T00:00:00Z");

        var result = rankingGeneration.generate(definitionId);

        assertThat(result.status()).isEqualTo("GENERATED");
        assertThat(result.entryCount()).isEqualTo(1);
        assertThat(scoreSource(result.rankingVersionId())).isEqualTo(highScore);
        String masked = studentDisplayName(result.rankingVersionId());
        assertThat(masked).startsWith("选手-");
        assertThat(masked).doesNotContain(studentWinnerId.toString().replace("-", ""));
        assertThat(schoolName(result.rankingVersionId())).isNull();
        assertThat(authorizationIdsSnapshot(result.rankingVersionId())).contains(authB.toString());
        assertThat(authorizationIdsSnapshot(result.rankingVersionId())).doesNotContain(authA.toString());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ranking_entry_score_sources res
                JOIN ranking_entries re ON re.id = res.entry_id
                JOIN ranking_versions rv ON rv.id = re.version_id
                WHERE rv.definition_id = ? AND res.score_attempt_id = ?
                """, Integer.class, definitionId, lowScore)).isZero();
    }

    private UUID createDefinition() {
        return rankingDefinitions.create("L3 Ranking " + runPrefix, projectId, ruleVersionId).id();
    }

    private UUID insertApprovedAuthorization(String dataScope, boolean allowSchoolName, boolean allowStudentName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO l3_authorizations(
                    id, school_id, project_id, rule_version_id, data_scope,
                    allow_school_name, allow_student_name, authorization_status,
                    reviewed_by, reviewed_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'APPROVED', ?, ?, ?, ?, 1)
                """,
                id, schoolId, projectId, ruleVersionId, dataScope,
                allowSchoolName, allowStudentName,
                adminId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return id;
    }

    private UUID insertScore(UUID activityId, UUID studentId, int scoreValue, boolean currentEffective, String businessTime) {
        UUID scoreAttemptId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO score_attempts(
                    id, school_id, activity_project_id, student_id, attempt_number,
                    score_storage_type, score_value, is_current_effective, score_status,
                    entered_by, score_business_time, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scoreAttemptId, schoolId, activityId, studentId, 1,
                "INTEGER", new BigDecimal(scoreValue), currentEffective, "APPROVED",
                adminId, Timestamp.from(Instant.parse(businessTime)), 1);
        return scoreAttemptId;
    }

    private UUID insertActivity(String title, Instant start, Instant end) {
        UUID activityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO activities(
                    id, school_id, title, start_time, end_time, execution_status, public_status, created_by
                ) VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 'PUBLIC', ?)
                """,
                activityId, schoolId, runPrefix + "-" + title,
                start == null ? null : Timestamp.from(start),
                end == null ? null : Timestamp.from(end),
                adminId);
        return activityId;
    }

    private UUID insertActivityProject(UUID activityId) {
        UUID activityProjectId = UUID.randomUUID();
        jdbc.update("INSERT INTO activity_projects(id, activity_id, project_id, rule_version_id) VALUES (?, ?, ?, ?)",
                activityProjectId, activityId, projectId, ruleVersionId);
        return activityProjectId;
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

    private void insertUser(UUID id, String label, String platformRole) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, runPrefix + "-" + label, "{noop}password", "NORMAL", platformRole);
    }

    private void insertMembership(UUID membershipId, UUID userId, UUID schoolId, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, membershipId, userId, schoolId, role);
    }

    private void insertStudentProfile(UUID id, UUID membershipId, String grade, String className) {
        jdbc.update("""
                INSERT INTO student_profiles(id, membership_id, grade, class_name, student_number)
                VALUES (?, ?, ?, ?, ?)
                """, id, membershipId, grade, className, runPrefix + "-num-" + id.toString().substring(0, 8));
    }

    private void insertProject(UUID projectId, UUID ruleVersionId) {
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id, name, category, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, project_status,
                    current_rule_version_id
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                projectId, runPrefix + "-project", "SPORTS", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "points", "BEST", "PUBLISHED", null);
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id, project_id, version_number, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, rules_text, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                ruleVersionId, projectId, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER",
                "points", "BEST", runPrefix + "-rules", adminId);
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = ? WHERE id = ?", ruleVersionId, projectId);
    }

    private void authenticateSuperAdmin() {
        var details = new CampusGuinnessUserDetails(
                superAdminId,
                runPrefix + "-super",
                "{noop}password",
                "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")),
                List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }

    private int entryCount(UUID versionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ranking_entries WHERE version_id = ?", Integer.class, versionId);
    }

    private String studentDisplayName(UUID versionId) {
        return jdbc.queryForObject("""
                SELECT student_display_name
                FROM ranking_entries
                WHERE version_id = ?
                ORDER BY rank_position ASC, student_id ASC
                LIMIT 1
                """, String.class, versionId);
    }

    private String schoolName(UUID versionId) {
        return jdbc.queryForObject("""
                SELECT school_name
                FROM ranking_entries
                WHERE version_id = ?
                ORDER BY rank_position ASC, student_id ASC
                LIMIT 1
                """, String.class, versionId);
    }

    private UUID scoreSource(UUID versionId) {
        return jdbc.queryForObject("""
                SELECT res.score_attempt_id
                FROM ranking_entry_score_sources res
                JOIN ranking_entries re ON re.id = res.entry_id
                WHERE re.version_id = ?
                ORDER BY re.rank_position ASC, re.student_id ASC
                LIMIT 1
                """, UUID.class, versionId);
    }

    private String authorizationIdsSnapshot(UUID versionId) {
        return jdbc.queryForObject("""
                SELECT authorization_ids_snapshot::text
                FROM ranking_versions
                WHERE id = ?
                """, String.class, versionId);
    }
}
