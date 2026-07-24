package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.port.StudentActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;

@Component
@Transactional(readOnly = true)
class StudentActivityQueryAdapter implements StudentActivityQueryPort {

    private final JdbcTemplate jdbc;

    StudentActivityQueryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public QueryPage<StudentActivityItem> findMine(UUID studentId, int page, int size) {
        String countSql = """
            SELECT count(DISTINCT a.id)
            FROM activity_participants ap
            JOIN school_memberships sm ON ap.student_membership_id = sm.id
            JOIN activities a ON ap.activity_id = a.id
            WHERE sm.user_id = ? AND sm.status = 'ACTIVE' AND sm.role_in_school = 'STUDENT'
            """;
        Long total = jdbc.queryForObject(countSql, Long.class, studentId);

        if (total == null || total == 0) {
            return new QueryPage<>(List.of(), page, size, 0);
        }

        String sql = """
            SELECT a.id, a.title,
                   CASE WHEN length(a.description) > 100 THEN left(a.description, 100) || '...' ELSE a.description END,
                   a.start_time, a.end_time, a.location, a.execution_status,
                   (SELECT count(*) FROM activity_project_participants app
                    JOIN activity_projects ap2 ON app.activity_project_id = ap2.id
                    JOIN activity_participants ap3 ON app.activity_participant_id = ap3.id
                    JOIN school_memberships sm2 ON ap3.student_membership_id = sm2.id
                    WHERE ap2.activity_id = a.id AND sm2.user_id = ? AND sm2.status = 'ACTIVE') AS project_count
            FROM activity_participants ap
            JOIN school_memberships sm ON ap.student_membership_id = sm.id
            JOIN activities a ON ap.activity_id = a.id
            WHERE sm.user_id = ? AND sm.status = 'ACTIVE' AND sm.role_in_school = 'STUDENT'
            GROUP BY a.id
            ORDER BY a.start_time DESC NULLS LAST, a.id DESC
            LIMIT ? OFFSET ?
            """;

        var items = jdbc.queryForList(sql, studentId, studentId, size, page * size).stream()
                .map(row -> new StudentActivityItem(
                        (UUID) row.get("id"),
                        (String) row.get("title"),
                        (String) row.get("description"),
                        row.get("start_time") != null ? ((Timestamp) row.get("start_time")).toInstant() : null,
                        row.get("end_time") != null ? ((Timestamp) row.get("end_time")).toInstant() : null,
                        (String) row.get("location"),
                        (String) row.get("execution_status"),
                        ((Number) row.get("project_count")).intValue()
                )).toList();

        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<StudentActivityDetail> findMineById(UUID studentId, UUID activityId) {
        // Verify ownership
        String checkSql = """
            SELECT 1 FROM activity_participants ap
            JOIN school_memberships sm ON ap.student_membership_id = sm.id
            WHERE sm.user_id = ? AND sm.status = 'ACTIVE' AND sm.role_in_school = 'STUDENT'
              AND ap.activity_id = ?
            """;
        var check = jdbc.queryForList(checkSql, studentId, activityId);
        if (check.isEmpty()) return Optional.empty();

        // Activity info
        String actSql = """
            SELECT id, title, description, start_time, end_time, location, execution_status
            FROM activities WHERE id = ?
            """;
        var act = jdbc.queryForMap(actSql, activityId);

        // My assigned projects with latest attempt
        String projSql = """
            SELECT app.id AS activity_project_id,
                   cp.id AS project_id, cp.name AS project_name, cp.category,
                   cp.score_storage_type, cp.score_unit,
                   sa.id AS latest_attempt_id, sa.score_status AS latest_attempt_status,
                   CASE cp.score_storage_type
                     WHEN 'INTEGER' THEN sa.score_value::text
                     WHEN 'DECIMAL' THEN sa.score_value::text
                     WHEN 'DURATION' THEN
                       CASE WHEN sa.score_duration_ms < 1000 THEN sa.score_duration_ms::text || 'ms'
                            WHEN sa.score_duration_ms < 60000 THEN (sa.score_duration_ms/1000)::text || '秒'
                            WHEN sa.score_duration_ms < 3600000 THEN (sa.score_duration_ms/60000)::text || '分' || ((sa.score_duration_ms%60000)/1000)::text || '秒'
                            ELSE (sa.score_duration_ms/3600000)::text || '时' || ((sa.score_duration_ms%3600000)/60000)::text || '分' || ((sa.score_duration_ms%60000)/1000)::text || '秒'
                       END
                     WHEN 'GRADE' THEN sa.score_grade
                   END AS latest_score_display,
                   EXISTS(SELECT 1 FROM score_attempts sa2
                          WHERE sa2.activity_project_id = app.activity_project_id
                            AND sa2.student_id = ?
                            AND sa2.score_status = 'APPROVED') AS has_approved
            FROM activity_project_participants app
            JOIN activity_projects ap2 ON app.activity_project_id = ap2.id
            JOIN activity_participants ap3 ON app.activity_participant_id = ap3.id
            JOIN school_memberships sm2 ON ap3.student_membership_id = sm2.id
            JOIN challenge_projects cp ON ap2.project_id = cp.id
            LEFT JOIN LATERAL (
              SELECT sa.* FROM score_attempts sa
              WHERE sa.activity_project_id = app.activity_project_id
                AND sa.student_id = ?
              ORDER BY COALESCE(sa.submitted_at, sa.created_at) DESC, sa.id DESC
              LIMIT 1
            ) sa ON true
            WHERE ap2.activity_id = ? AND sm2.user_id = ? AND sm2.status = 'ACTIVE'
            """;

        var projects = jdbc.queryForList(projSql, studentId, studentId, activityId, studentId).stream()
                .map(row -> new AssignedProjectItem(
                        (UUID) row.get("activity_project_id"),
                        (UUID) row.get("project_id"),
                        (String) row.get("project_name"),
                        (String) row.get("category"),
                        (String) row.get("score_storage_type"),
                        (String) row.get("score_unit"),
                        (UUID) row.get("latest_attempt_id"),
                        (String) row.get("latest_attempt_status"),
                        (String) row.get("latest_score_display"),
                        (Boolean) row.get("has_approved")
                )).toList();

        return Optional.of(new StudentActivityDetail(
                (UUID) act.get("id"), (String) act.get("title"),
                (String) act.get("description"),
                act.get("start_time") != null ? ((Timestamp) act.get("start_time")).toInstant() : null,
                act.get("end_time") != null ? ((Timestamp) act.get("end_time")).toInstant() : null,
                (String) act.get("location"), (String) act.get("execution_status"),
                projects));
    }
}
