package com.campusguinness.project.application.service;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
class ProjectPublishRollbackIT extends PostgreSqlIntegrationTestSupport {

    @Autowired ChallengeProjectApplicationService service;
    @Autowired JdbcTemplate jdbc;

    UUID projectId;

    @BeforeEach void setUp() {
        projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO challenge_projects(id,name,category,score_storage_type,score_indicator_type,comparison_direction,allow_tie,effective_score_rule,project_status) VALUES (?,?,?,?,?,?,?,?,?)",
                projectId, "Rollback Test", "SPEED", "INTEGER", "NUMERIC", "HIGHER_BETTER", true, "BEST", "DRAFT");
    }

    @AfterEach void tearDown() {
        jdbc.update("UPDATE challenge_projects SET current_rule_version_id=NULL WHERE id=?", projectId);
        jdbc.update("DELETE FROM project_rule_versions WHERE project_id=?", projectId);
        jdbc.update("DELETE FROM challenge_projects WHERE id=?", projectId);
    }

    @Test @DisplayName("publish rollback when rule version creation fails — project stays DRAFT")
    void publishRollbackWhenRuleVersionCreationFails() {
        UUID missingActorId = UUID.randomUUID();

        // Publish with an actorId that doesn't exist in users table
        // The project_rule_versions.created_by FK will fail
        assertThatThrownBy(() -> service.publish(projectId, missingActorId))
                .isInstanceOf(Exception.class);

        // Verify rollback: project status must still be DRAFT
        String status = jdbc.queryForObject(
                "SELECT project_status FROM challenge_projects WHERE id=?",
                String.class, projectId);
        assertThat(status).isEqualTo("DRAFT");

        // Verify no current_rule_version_id was set
        UUID currentRv = jdbc.queryForObject(
                "SELECT current_rule_version_id FROM challenge_projects WHERE id=?",
                UUID.class, projectId);
        assertThat(currentRv).isNull();

        // Verify no rule version was created
        Integer ruleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_rule_versions WHERE project_id=?",
                Integer.class, projectId);
        assertThat(ruleCount).isEqualTo(0);
    }

    @Test @DisplayName("publish creates rule version with valid actorId")
    void publishCreatesRuleVersionWithValidActor() {
        UUID actorId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)",
                actorId, "pub-" + UUID.randomUUID().toString().substring(0, 6),
                "$2a$10$hash0000000000000000000000", "NORMAL");

        try {
            var result = service.publish(projectId, actorId);
            assertThat(result.status()).isEqualTo("PUBLISHED");

            // Verify project_status is PUBLISHED
            String status = jdbc.queryForObject(
                    "SELECT project_status FROM challenge_projects WHERE id=?",
                    String.class, projectId);
            assertThat(status).isEqualTo("PUBLISHED");

            // Verify current_rule_version_id is set
            UUID currentRv = jdbc.queryForObject(
                    "SELECT current_rule_version_id FROM challenge_projects WHERE id=?",
                    UUID.class, projectId);
            assertThat(currentRv).isNotNull();

            // Verify rule version was created
            Integer ruleCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_rule_versions WHERE project_id=? AND version_number=1",
                    Integer.class, projectId);
            assertThat(ruleCount).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM users WHERE id=?", actorId);
        }
    }
}
