package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.application.port.ProjectRuleVersionPort;

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
    public UUID createInitialRuleVersion(UUID projectId, InitialRuleVersionSnapshot s, UUID createdBy) {
        UUID ruleVersionId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO project_rule_versions(id,project_id,version_number,score_storage_type,score_indicator_type,comparison_direction,effective_score_rule,score_unit,decimal_places,grade_order,rules_text,venue_requirements,equipment_requirements,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ruleVersionId, projectId, 1,
                s.scoreStorageType(), s.scoreIndicatorType(),
                s.comparisonDirection(), s.effectiveScoreRule(),
                s.scoreUnit(), s.decimalPlaces(), s.gradeOrder(), s.rulesText(),
                s.venueRequirements(), s.equipmentRequirements(),
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
