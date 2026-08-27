package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ActivityProjectLockPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
class ActivityProjectLockAdapter implements ActivityProjectLockPort {
    private final JdbcTemplate jdbc;

    ActivityProjectLockAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public Optional<Scope> lock(UUID activityProjectId) {
        return jdbc.query("""
                SELECT ap.id, ap.project_id, ap.rule_version_id,
                       rv.effective_score_rule, rv.score_storage_type,
                       rv.comparison_direction, rv.grade_order, rv.allow_tie
                FROM activity_projects ap
                JOIN project_rule_versions rv
                  ON rv.id = ap.rule_version_id AND rv.project_id = ap.project_id
                WHERE ap.id = ?
                FOR UPDATE OF ap
                """, rs -> rs.next() ? Optional.of(new Scope(
                rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("rule_version_id", UUID.class), rs.getString("effective_score_rule"),
                rs.getString("score_storage_type"), rs.getString("comparison_direction"),
                rs.getString("grade_order"), rs.getBoolean("allow_tie"))) : Optional.empty(), activityProjectId);
    }
}
