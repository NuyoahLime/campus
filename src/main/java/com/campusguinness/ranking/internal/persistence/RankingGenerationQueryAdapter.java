package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import com.campusguinness.ranking.application.query.port.RankingGenerationQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class RankingGenerationQueryAdapter implements RankingGenerationQueryPort {
    private final JdbcTemplate jdbc;

    RankingGenerationQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RankingGenerationContext> findContext(UUID activityProjectId) {
        return jdbc.query("""
                SELECT ap.id AS activity_project_id, a.id AS activity_id, a.title AS activity_title,
                       a.school_id, s.name AS school_name, ap.project_id, p.name AS project_name,
                       ap.rule_version_id, prv.version_number AS rule_version_number,
                       prv.score_storage_type, prv.comparison_direction, prv.decimal_places, prv.grade_order
                FROM activity_projects ap
                JOIN activities a ON a.id = ap.activity_id
                JOIN schools s ON s.id = a.school_id
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN project_rule_versions prv ON prv.id = ap.rule_version_id AND prv.project_id = ap.project_id
                WHERE ap.id = ?
                """, rs -> rs.next() ? Optional.of(mapContext(rs)) : Optional.empty(), activityProjectId);
    }

    @Override
    public List<RankingGenerationSourceRow> findAuthoritativeEffectiveScores(UUID activityProjectId, UUID schoolId) {
        return jdbc.query("""
                SELECT sa.id AS score_attempt_id, sa.student_id, u.username AS student_display_name,
                       sa.score_value, sa.score_duration_ms, sa.score_grade
                FROM score_attempts sa
                JOIN users u ON u.id = sa.student_id
                WHERE sa.activity_project_id = ? AND sa.school_id = ? AND sa.score_status = 'APPROVED'
                  AND sa.is_current_effective = true
                ORDER BY LOWER(u.username), sa.student_id, sa.id
                """, this::mapSource, activityProjectId, schoolId);
    }

    private RankingGenerationContext mapContext(ResultSet rs) throws SQLException {
        return new RankingGenerationContext(
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getObject("school_id", UUID.class),
                rs.getString("school_name"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getObject("rule_version_id", UUID.class),
                rs.getInt("rule_version_number"),
                rs.getString("score_storage_type"),
                rs.getString("comparison_direction"),
                (Integer) rs.getObject("decimal_places"),
                rs.getString("grade_order"));
    }

    private RankingGenerationSourceRow mapSource(ResultSet rs, int row) throws SQLException {
        return new RankingGenerationSourceRow(
                rs.getObject("score_attempt_id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_display_name"),
                (BigDecimal) rs.getObject("score_value"),
                (Long) rs.getObject("score_duration_ms"),
                rs.getString("score_grade"));
    }
}
