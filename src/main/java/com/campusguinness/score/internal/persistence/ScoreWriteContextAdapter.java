package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ScoreWriteContextPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ScoreWriteContextAdapter implements ScoreWriteContextPort {
    private static final List<String> FILTERABLE_STATUSES = List.of(
            "DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED", "INVALIDATED");

    private final JdbcTemplate jdbc;

    ScoreWriteContextAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Activity> findActivity(UUID activityId) {
        return jdbc.query("""
                SELECT id, school_id, title, execution_status
                FROM activities WHERE id = ?
                """, rs -> rs.next() ? Optional.of(new Activity(
                rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
                rs.getString("title"), rs.getString("execution_status"))) : Optional.empty(), activityId);
    }

    @Override
    public Optional<Context> findContext(UUID activityProjectId) {
        return jdbc.query("""
                SELECT ap.activity_id, ap.id AS activity_project_id, a.school_id, ap.rule_version_id,
                       a.title AS activity_title, a.execution_status, p.name AS project_name,
                       rv.score_storage_type, rv.decimal_places, rv.grade_order
                FROM activity_projects ap
                JOIN activities a ON a.id = ap.activity_id
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN project_rule_versions rv
                  ON rv.id = ap.rule_version_id AND rv.project_id = ap.project_id
                WHERE ap.id = ?
                """, rs -> rs.next() ? Optional.of(new Context(
                rs.getObject("activity_id", UUID.class),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("school_id", UUID.class),
                rs.getObject("rule_version_id", UUID.class),
                rs.getString("activity_title"), rs.getString("execution_status"),
                rs.getString("project_name"), rs.getString("score_storage_type"),
                (Integer) rs.getObject("decimal_places"), rs.getString("grade_order"))) : Optional.empty(),
                activityProjectId);
    }

    @Override
    public Optional<Student> findActiveStudent(UUID studentId, UUID schoolId) {
        List<Student> rows = jdbc.query("""
                SELECT user_id, id FROM school_memberships
                WHERE user_id = ? AND school_id = ? AND status = 'ACTIVE'
                  AND role_in_school = 'STUDENT'
                ORDER BY started_at, id
                """, (rs, row) -> new Student(rs.getObject("user_id", UUID.class),
                rs.getObject("id", UUID.class)), studentId, schoolId);
        return rows.size() == 1 ? Optional.of(rows.getFirst()) : Optional.empty();
    }

    @Override
    public boolean isParticipant(UUID activityId, UUID membershipId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM activity_participants
                WHERE activity_id = ? AND student_membership_id = ?
                """, Integer.class, activityId, membershipId);
        return count != null && count == 1;
    }

    @Override
    @Transactional
    public int nextAttemptNumber(UUID activityProjectId, UUID studentId) {
        jdbc.queryForObject("SELECT id FROM activity_projects WHERE id = ? FOR UPDATE",
                UUID.class, activityProjectId);
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_number), 0) + 1
                FROM score_attempts WHERE activity_project_id = ? AND student_id = ?
                """, Integer.class, activityProjectId, studentId);
        return next == null ? 1 : next;
    }

    @Override
    public List<CandidateRow> findCandidates(UUID activityId, UUID schoolId) {
        return jdbc.query("""
                SELECT sm.user_id AS student_id, u.username AS display_name, sp.student_number,
                       apj.id AS activity_project_id, p.name AS project_name, rv.score_storage_type,
                       latest.id AS latest_attempt_id, latest.attempt_number AS latest_attempt_number,
                       latest.score_status AS latest_status
                FROM activity_participants apart
                JOIN activities a ON a.id = apart.activity_id AND a.school_id = ?
                JOIN school_memberships sm ON sm.id = apart.student_membership_id
                  AND sm.school_id = a.school_id AND sm.role_in_school = 'STUDENT' AND sm.status = 'ACTIVE'
                JOIN users u ON u.id = sm.user_id
                LEFT JOIN student_profiles sp ON sp.membership_id = sm.id
                JOIN activity_projects apj ON apj.activity_id = a.id
                JOIN challenge_projects p ON p.id = apj.project_id
                JOIN project_rule_versions rv ON rv.id = apj.rule_version_id AND rv.project_id = apj.project_id
                LEFT JOIN LATERAL (
                    SELECT sa.id, sa.attempt_number, sa.score_status
                    FROM score_attempts sa
                    WHERE sa.activity_project_id = apj.id AND sa.student_id = sm.user_id
                    ORDER BY sa.attempt_number DESC, sa.id DESC LIMIT 1
                ) latest ON true
                WHERE a.id = ?
                ORDER BY LOWER(u.username), sm.user_id, LOWER(p.name), apj.id
                """, (rs, row) -> new CandidateRow(
                rs.getObject("student_id", UUID.class), rs.getString("display_name"),
                rs.getString("student_number"), rs.getObject("activity_project_id", UUID.class),
                rs.getString("project_name"), rs.getString("score_storage_type"),
                rs.getObject("latest_attempt_id", UUID.class),
                (Integer) rs.getObject("latest_attempt_number"), rs.getString("latest_status")),
                schoolId, activityId);
    }

    @Override
    public List<ScoreRow> findScores(UUID activityId, UUID schoolId, UUID activityProjectId, String status) {
        if (status != null && !FILTERABLE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported score status filter");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT sa.id AS score_attempt_id, a.id AS activity_id, a.title AS activity_title,
                       ap.id AS activity_project_id, p.name AS project_name, sa.student_id,
                       u.username AS student_display, sp.student_number, sa.attempt_number,
                       sa.score_status, sa.score_storage_type, sa.score_value,
                       sa.score_duration_ms, sa.score_grade, sa.score_business_time,
                       sa.is_current_effective
                FROM score_attempts sa
                JOIN activity_projects ap ON ap.id = sa.activity_project_id
                JOIN activities a ON a.id = ap.activity_id AND a.school_id = sa.school_id
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN users u ON u.id = sa.student_id
                LEFT JOIN school_memberships sm ON sm.user_id = sa.student_id
                  AND sm.school_id = sa.school_id AND sm.role_in_school = 'STUDENT'
                LEFT JOIN student_profiles sp ON sp.membership_id = sm.id
                WHERE a.id = ? AND a.school_id = ?
                """);
        List<Object> args = new ArrayList<>(List.of(activityId, schoolId));
        if (activityProjectId != null) {
            sql.append(" AND ap.id = ?");
            args.add(activityProjectId);
        }
        if (status != null) {
            sql.append(" AND sa.score_status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY LOWER(u.username), ap.id, sa.attempt_number DESC, sa.id DESC");
        return jdbc.query(sql.toString(), this::mapScore, args.toArray());
    }

    @Override
    public Optional<ScoreRow> findScore(UUID scoreAttemptId) {
        List<ScoreRow> rows = jdbc.query("""
                SELECT sa.id AS score_attempt_id, a.id AS activity_id, a.title AS activity_title,
                       ap.id AS activity_project_id, p.name AS project_name, sa.student_id,
                       u.username AS student_display, sp.student_number, sa.attempt_number,
                       sa.score_status, sa.score_storage_type, sa.score_value,
                       sa.score_duration_ms, sa.score_grade, sa.score_business_time,
                       sa.is_current_effective
                FROM score_attempts sa
                JOIN activity_projects ap ON ap.id = sa.activity_project_id
                JOIN activities a ON a.id = ap.activity_id AND a.school_id = sa.school_id
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN users u ON u.id = sa.student_id
                LEFT JOIN school_memberships sm ON sm.user_id = sa.student_id
                  AND sm.school_id = sa.school_id AND sm.role_in_school = 'STUDENT'
                LEFT JOIN student_profiles sp ON sp.membership_id = sm.id
                WHERE sa.id = ?
                """, this::mapScore, scoreAttemptId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private ScoreRow mapScore(ResultSet rs, int row) throws SQLException {
        var timestamp = rs.getTimestamp("score_business_time");
        Instant businessTime = timestamp == null ? null : timestamp.toInstant();
        return new ScoreRow(rs.getObject("score_attempt_id", UUID.class),
                rs.getObject("activity_id", UUID.class), rs.getString("activity_title"),
                rs.getObject("activity_project_id", UUID.class), rs.getString("project_name"),
                rs.getObject("student_id", UUID.class), rs.getString("student_display"),
                rs.getString("student_number"), rs.getInt("attempt_number"),
                rs.getString("score_status"), rs.getString("score_storage_type"),
                rs.getBigDecimal("score_value"), (Long) rs.getObject("score_duration_ms"),
                rs.getString("score_grade"), businessTime, rs.getBoolean("is_current_effective"));
    }
}
