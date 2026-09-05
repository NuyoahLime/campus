package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.port.L3AuthorizationValidationPort;
import com.campusguinness.ranking.application.service.L3AuthorizationScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Transactional(readOnly = true)
class L3AuthorizationValidationAdapter implements L3AuthorizationValidationPort {
    private final JdbcTemplate jdbc;

    L3AuthorizationValidationAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void validateProjectRuleVersion(UUID projectId, UUID ruleVersionId) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM challenge_projects p
                JOIN project_rule_versions prv ON prv.project_id = p.id
                WHERE p.id = ? AND p.project_status = 'PUBLISHED' AND prv.id = ?
                """, Integer.class, projectId, ruleVersionId);
        if (matches == null || matches != 1) {
            throw new IllegalStateException(
                    "Cannot save L3 authorization: ChallengeProject and RuleVersion must exist and match.");
        }
    }

    @Override
    public void validateSchoolScope(UUID schoolId, UUID projectId, UUID ruleVersionId, L3AuthorizationScope scope) {
        if (scope.activityIds().isEmpty()) {
            return;
        }
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT a.id)
                FROM activities a
                JOIN activity_projects ap ON ap.activity_id = a.id
                WHERE a.id = ANY (?::uuid[])
                  AND a.school_id = ?
                  AND ap.project_id = ?
                  AND ap.rule_version_id = ?
                """, Integer.class, scope.activityIds().toArray(UUID[]::new), schoolId, projectId, ruleVersionId);
        if (matches == null || matches != scope.activityIds().size()) {
            throw new IllegalStateException(
                    "Cannot save L3 authorization: dataScope.activityIds must belong to the school, ChallengeProject, and RuleVersion.");
        }
    }

    @Override
    public void validateSchoolNormal(UUID schoolId) {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM schools WHERE id = ? AND school_status = 'NORMAL'
                """, Integer.class, schoolId);
        if (matches == null || matches != 1) {
            throw new IllegalStateException("Cannot approve L3 authorization: school must be NORMAL.");
        }
    }
}
