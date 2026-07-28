package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.application.port.ProjectRuleVersionPort;
import com.campusguinness.project.internal.domain.ScoreConfig;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
class ProjectRuleVersionAdapter implements ProjectRuleVersionPort {

    private final JdbcTemplate jdbc;

    ProjectRuleVersionAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public UUID createInitialRuleVersion(UUID projectId, ScoreConfig sc, UUID createdBy) {
        UUID ruleVersionId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,score_unit,decimal_places,grade_order,rules_text,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 1,
                sc.storageType().name(), sc.indicatorType().name(),
                sc.comparisonDirection().name(), sc.effectiveScoreRule(),
                sc.scoreUnit(), sc.decimalPlaces(), sc.gradeOrder(), sc.rulesText(),
                createdBy);

        jdbc.update(
                "UPDATE challenge_projects SET current_rule_version_id=? WHERE id=?",
                ruleVersionId, projectId);

        return ruleVersionId;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCurrentRuleVersionId(UUID projectId) {
        var list = jdbc.query(
                "SELECT current_rule_version_id FROM challenge_projects WHERE id=?",
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                projectId);
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.getFirst());
    }
}
