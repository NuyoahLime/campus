package com.campusguinness.project.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.project.application.port.ProjectRuleVersionRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionSnapshot;
import com.campusguinness.project.internal.domain.ComparisonDirection;
import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.project.internal.domain.ScoreIndicatorType;
import com.campusguinness.project.internal.domain.ScoreStorageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ProjectRuleVersionRepositoryAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired ProjectRuleVersionRepository ruleVersions;
    @Autowired ChallengeProjectJpaRepository projects;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsCompleteSnapshotAndCurrentVersionForeignKey() {
        UUID actorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("insert into users (id, username, password_hash, account_status, platform_role) values (?, ?, ?, 'NORMAL', 'SUPER_ADMIN')",
                actorId, "stage16-" + actorId, "not-used");

        var project = project(projectId);
        projects.saveAndFlush(project);
        var score = new ScoreConfig(ScoreStorageType.DECIMAL, ScoreIndicatorType.NUMERIC,
                ComparisonDirection.HIGHER_BETTER, "points", 2, "BEST",
                null, "Frozen rules", false);
        ruleVersions.save(new ProjectRuleVersionSnapshot(versionId, projectId, 1, score,
                "Main gym", "Electronic timer", "Initial release", actorId, Instant.now()));

        project.setCurrentRuleVersionId(versionId);
        projects.saveAndFlush(project);

        var snapshots = ruleVersions.findAllByProjectId(projectId);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).scoreConfig().allowTie()).isFalse();
        assertThat(snapshots.get(0).scoreConfig().rulesText()).isEqualTo("Frozen rules");
        assertThat(snapshots.get(0).venueRequirements()).isEqualTo("Main gym");
        assertThat(jdbc.queryForObject("select current_rule_version_id from challenge_projects where id = ?",
                UUID.class, projectId)).isEqualTo(versionId);
    }

    private ChallengeProjectEntity project(UUID id) {
        var entity = new ChallengeProjectEntity();
        entity.setId(id);
        entity.setName("Stage 16 project");
        entity.setCategory("ATHLETICS");
        entity.setDescription("Persistence verification");
        entity.setVenueRequirements("Main gym");
        entity.setEquipmentRequirements("Timer");
        entity.setRulesText("Frozen rules");
        entity.setScoreStorageType("DECIMAL");
        entity.setScoreIndicatorType("NUMERIC");
        entity.setComparisonDirection("HIGHER_BETTER");
        entity.setScoreUnit("points");
        entity.setDecimalPlaces(2);
        entity.setAllowTie(false);
        entity.setEffectiveScoreRule("BEST");
        entity.setProjectStatus("PUBLISHED");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
