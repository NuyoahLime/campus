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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class L3RankingManagementApplicationServiceIT extends PostgreSqlIntegrationTestSupport {
    @Autowired private L3RankingDefinitionApplicationService definitions;
    @Autowired private L3RankingManagementApplicationService l3Management;
    @Autowired private RankingManagementQueryService managementQuery;
    @Autowired private RankingDefinitionApplicationService schoolManagement;
    @Autowired private JdbcTemplate jdbc;

    private final String runPrefix = "l3-mgmt-" + UUID.randomUUID().toString().substring(0, 8);
    private UUID schoolId;
    private UUID schoolAdminId;
    private UUID superAdminId;
    private UUID creatorId;
    private UUID projectId;
    private UUID ruleVersionId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        schoolAdminId = UUID.randomUUID();
        superAdminId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ruleVersionId = UUID.randomUUID();

        insertSchool();
        insertUser(schoolAdminId, "school-admin", null);
        insertUser(superAdminId, "super-admin", "SUPER_ADMIN");
        insertUser(creatorId, "creator", null);
        insertMembership(schoolAdminId, schoolId, "SCHOOL_ADMIN");
        insertProject(projectId, ruleVersionId, creatorId);
        authenticateSuperAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM ranking_definitions WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id = NULL WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM project_rule_versions WHERE project_id IN (SELECT id FROM challenge_projects WHERE name LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM challenge_projects WHERE name LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                runPrefix + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM schools WHERE name LIKE ?", runPrefix + "%");
    }

    @Test
    void listAndDetailExposeOnlyValidPlatformL3Definitions() {
        UUID validId = definitions.create(runPrefix + "-valid", projectId, ruleVersionId).id();
        UUID schoolScopedL3Id = insertDefinition("L3", runPrefix + "-school-scoped", schoolId, projectId,
                ruleVersionId);
        UUID l1Id = insertDefinition("L1", runPrefix + "-l1", schoolId, projectId, ruleVersionId);
        UUID otherProjectId = UUID.randomUUID();
        UUID otherRuleVersionId = UUID.randomUUID();
        insertProject(otherProjectId, otherRuleVersionId, creatorId);
        UUID crossProjectId = insertDefinition("L3", runPrefix + "-cross-project", null, projectId,
                otherRuleVersionId);

        var page = managementQuery.listL3(0, 20);

        assertThat(page.items()).extracting("id")
                .contains(validId)
                .doesNotContain(schoolScopedL3Id, l1Id, crossProjectId);
        var detail = managementQuery.detailL3(validId);
        assertThat(detail.layer()).isEqualTo("L3");
        assertThat(detail.schoolId()).isNull();
        assertThat(detail.projectId()).isEqualTo(projectId);
        assertThat(detail.ruleVersionId()).isEqualTo(ruleVersionId);
        assertThat(detail.ruleVersionNumber()).isEqualTo(1);
        assertThatThrownBy(() -> managementQuery.detailL3(schoolScopedL3Id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void superAdminCanEnableAndDisablePlatformL3() {
        UUID definitionId = definitions.create(runPrefix + "-toggle", projectId, ruleVersionId).id();

        var disabled = l3Management.disable(definitionId);
        assertThat(disabled.enabled()).isFalse();
        assertThat(enabled(definitionId)).isFalse();

        var enabled = l3Management.enable(definitionId);
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled(definitionId)).isTrue();
    }

    @Test
    void invalidDefinitionsAreRejectedWithoutMutation() {
        UUID l1Id = insertDefinition("L1", runPrefix + "-l1-invalid", schoolId, projectId, ruleVersionId);
        UUID l2Id = insertDefinition("L2", runPrefix + "-l2-invalid", schoolId, projectId, ruleVersionId);
        UUID corruptL3Id = insertDefinition("L3", runPrefix + "-l3-invalid", schoolId, projectId, ruleVersionId);

        assertThatThrownBy(() -> l3Management.disable(l1Id))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> l3Management.disable(l2Id))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> l3Management.disable(corruptL3Id))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(enabled(l1Id)).isTrue();
        assertThat(enabled(l2Id)).isTrue();
        assertThat(enabled(corruptL3Id)).isTrue();

        UUID validId = definitions.create(runPrefix + "-school-admin-target", projectId, ruleVersionId).id();
        authenticateSchoolAdmin();
        assertThatThrownBy(() -> schoolManagement.disable(validId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(enabled(validId)).isTrue();
    }

    private boolean enabled(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_enabled FROM ranking_definitions WHERE id = ?", Boolean.class, id));
    }

    private UUID insertDefinition(String layer, String name, UUID school, UUID project, UUID ruleVersion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ranking_definitions(
                    id, layer, name, school_id, project_id, dimension_filters, created_by
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                id, layer, name, school, project,
                "{\"ruleVersionId\":\"" + ruleVersion + "\"}", creatorId);
        return id;
    }

    private void insertSchool() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO schools(
                    id, name, unified_code_type, unified_code, internal_code, school_type, region,
                    address, contact_name, contact_phone, contact_email, school_status
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                schoolId, runPrefix + "-school", "USCC", runPrefix + "-" + suffix + "-uc",
                runPrefix + "-" + suffix + "-ic", "PRIMARY", "Beijing", "Address", "Contact",
                "13800000000", runPrefix + "@example.com", "NORMAL");
    }

    private void insertUser(UUID id, String label, String platformRole) {
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                id, runPrefix + "-" + label, "{noop}password", "NORMAL", platformRole);
    }

    private void insertMembership(UUID userId, UUID school, String role) {
        jdbc.update("""
                INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), userId, school, role);
    }

    private void insertProject(UUID project, UUID ruleVersion, UUID createdBy) {
        jdbc.update("""
                INSERT INTO challenge_projects(
                    id, name, category, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, project_status
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """,
                project, runPrefix + "-project-" + project.toString().substring(0, 8), "SPORTS",
                "INTEGER", "NUMERIC", "HIGHER_BETTER", "points", "BEST", "PUBLISHED");
        jdbc.update("""
                INSERT INTO project_rule_versions(
                    id, project_id, version_number, score_storage_type, score_indicator_type,
                    comparison_direction, score_unit, effective_score_rule, rules_text, created_by
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                ruleVersion, project, 1, "INTEGER", "NUMERIC", "HIGHER_BETTER",
                "points", "BEST", runPrefix + "-rules", createdBy);
    }

    private void authenticateSuperAdmin() {
        authenticate(superAdminId, "SUPER_ADMIN", List.of());
    }

    private void authenticateSchoolAdmin() {
        authenticate(schoolAdminId, "SCHOOL_ADMIN",
                List.of(new AuthenticatedSchoolMembership(UUID.randomUUID(), schoolId, "SCHOOL_ADMIN")));
    }

    private void authenticate(UUID userId, String role, List<AuthenticatedSchoolMembership> memberships) {
        var details = new CampusGuinnessUserDetails(
                userId, runPrefix + "-" + role.toLowerCase(), "{noop}password", "NORMAL",
                Set.of(new SimpleGrantedAuthority("ROLE_" + role)), memberships);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "n/a", details.getAuthorities()));
    }
}
