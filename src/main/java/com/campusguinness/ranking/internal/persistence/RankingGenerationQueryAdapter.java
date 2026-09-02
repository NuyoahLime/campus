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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

    @Override
    public List<RankingGenerationContext> findL2CandidateContexts(
            UUID projectId,
            UUID schoolId,
            String grade,
            String className,
            Instant activityPeriodStart,
            Instant activityPeriodEnd) {
        return jdbc.query("""
                SELECT NULL::uuid AS activity_project_id, NULL::uuid AS activity_id,
                       NULL::text AS activity_title, a.school_id, s.name AS school_name,
                       ap.project_id, p.name AS project_name, ap.rule_version_id,
                       prv.version_number AS rule_version_number, prv.score_storage_type,
                       prv.comparison_direction, prv.decimal_places, prv.grade_order
                FROM score_attempts sa
                JOIN activity_projects ap ON ap.id = sa.activity_project_id
                JOIN activities a ON a.id = ap.activity_id
                JOIN schools s ON s.id = a.school_id
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN project_rule_versions prv ON prv.id = ap.rule_version_id AND prv.project_id = ap.project_id
                LEFT JOIN school_memberships sm ON sm.user_id = sa.student_id
                    AND sm.school_id = sa.school_id
                    AND sm.role_in_school = 'STUDENT'
                    AND sm.status = 'ACTIVE'
                LEFT JOIN student_profiles sp ON sp.membership_id = sm.id
                WHERE ap.project_id = ?
                  AND a.school_id = ?
                  AND sa.school_id = ?
                  AND sa.score_status = 'APPROVED'
                  AND sa.is_current_effective = true
                  AND sa.score_storage_type = prv.score_storage_type
                  AND (?::text IS NULL OR sp.grade = ?::text)
                  AND (?::text IS NULL OR sp.class_name = ?::text)
                  AND (?::timestamptz IS NULL OR a.start_time >= ?::timestamptz)
                  AND (?::timestamptz IS NULL OR a.end_time <= ?::timestamptz)
                GROUP BY a.school_id, s.name, ap.project_id, p.name, ap.rule_version_id,
                         prv.version_number, prv.score_storage_type, prv.comparison_direction,
                         prv.decimal_places, prv.grade_order
                ORDER BY prv.version_number, ap.rule_version_id
                """,
                ps -> {
                    ps.setObject(1, projectId);
                    ps.setObject(2, schoolId);
                    ps.setObject(3, schoolId);
                    ps.setString(4, grade);
                    ps.setString(5, grade);
                    ps.setString(6, className);
                    ps.setString(7, className);
                    ps.setTimestamp(8, timestamp(activityPeriodStart));
                    ps.setTimestamp(9, timestamp(activityPeriodStart));
                    ps.setTimestamp(10, timestamp(activityPeriodEnd));
                    ps.setTimestamp(11, timestamp(activityPeriodEnd));
                },
                rs -> {
                    List<RankingGenerationContext> contexts = new ArrayList<>();
                    while (rs.next()) {
                        contexts.add(mapContext(rs));
                    }
                    return contexts;
                });
    }

    @Override
    public Optional<RankingGenerationContext> findL2FallbackContext(UUID projectId, UUID schoolId) {
        return jdbc.query("""
                SELECT NULL::uuid AS activity_project_id, NULL::uuid AS activity_id,
                       NULL::text AS activity_title, s.id AS school_id, s.name AS school_name,
                       p.id AS project_id, p.name AS project_name, prv.id AS rule_version_id,
                       prv.version_number AS rule_version_number, prv.score_storage_type,
                       prv.comparison_direction, prv.decimal_places, prv.grade_order
                FROM challenge_projects p
                JOIN schools s ON s.id = ?
                JOIN LATERAL (
                    SELECT COALESCE(p.current_rule_version_id, (
                        SELECT id
                        FROM project_rule_versions
                        WHERE project_id = p.id
                        ORDER BY version_number DESC, created_at DESC, id DESC
                        LIMIT 1
                    )) AS id
                ) selected_rule ON true
                JOIN project_rule_versions prv ON prv.id = selected_rule.id AND prv.project_id = p.id
                WHERE p.id = ?
                """, rs -> rs.next() ? Optional.of(mapContext(rs)) : Optional.empty(), schoolId, projectId);
    }

    @Override
    public List<RankingGenerationSourceRow> findL2AuthoritativeEffectiveScores(
            UUID projectId,
            UUID schoolId,
            UUID ruleVersionId,
            String grade,
            String className,
            Instant activityPeriodStart,
            Instant activityPeriodEnd) {
        return jdbc.query("""
                SELECT sa.id AS score_attempt_id, sa.student_id, u.username AS student_display_name,
                       sa.score_value, sa.score_duration_ms, sa.score_grade,
                       sa.activity_project_id, ap.rule_version_id
                FROM score_attempts sa
                JOIN activity_projects ap ON ap.id = sa.activity_project_id
                JOIN activities a ON a.id = ap.activity_id
                JOIN project_rule_versions prv ON prv.id = ap.rule_version_id AND prv.project_id = ap.project_id
                JOIN users u ON u.id = sa.student_id
                LEFT JOIN school_memberships sm ON sm.user_id = sa.student_id
                    AND sm.school_id = sa.school_id
                    AND sm.role_in_school = 'STUDENT'
                    AND sm.status = 'ACTIVE'
                LEFT JOIN student_profiles sp ON sp.membership_id = sm.id
                WHERE ap.project_id = ?
                  AND a.school_id = ?
                  AND sa.school_id = ?
                  AND ap.rule_version_id = ?
                  AND sa.score_status = 'APPROVED'
                  AND sa.is_current_effective = true
                  AND sa.score_storage_type = prv.score_storage_type
                  AND (?::text IS NULL OR sp.grade = ?::text)
                  AND (?::text IS NULL OR sp.class_name = ?::text)
                  AND (?::timestamptz IS NULL OR a.start_time >= ?::timestamptz)
                  AND (?::timestamptz IS NULL OR a.end_time <= ?::timestamptz)
                ORDER BY sa.student_id, sa.activity_project_id, sa.id
                """, this::mapL2Source,
                projectId, schoolId, schoolId, ruleVersionId,
                grade, grade,
                className, className,
                timestamp(activityPeriodStart), timestamp(activityPeriodStart),
                timestamp(activityPeriodEnd), timestamp(activityPeriodEnd));
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

    private RankingGenerationSourceRow mapL2Source(ResultSet rs, int row) throws SQLException {
        return new RankingGenerationSourceRow(
                rs.getObject("score_attempt_id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_display_name"),
                (BigDecimal) rs.getObject("score_value"),
                (Long) rs.getObject("score_duration_ms"),
                rs.getString("score_grade"),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("rule_version_id", UUID.class));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
