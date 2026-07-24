package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.port.StudentProjectQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.ScoreDisplayFormatter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;

@Component
@Transactional(readOnly = true)
class StudentProjectQueryAdapter implements StudentProjectQueryPort {

    private final JdbcTemplate jdbc;

    StudentProjectQueryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String BASE_FROM = """
        FROM activity_project_participants app
        JOIN activity_projects ap2 ON app.activity_project_id = ap2.id
        JOIN activity_participants ap3 ON app.activity_participant_id = ap3.id
        JOIN school_memberships sm ON ap3.student_membership_id = sm.id
        JOIN activities a ON ap2.activity_id = a.id
        JOIN challenge_projects cp ON ap2.project_id = cp.id
        """;

    private static final String OWNER_WHERE = """
        sm.user_id = ? AND sm.status = 'ACTIVE' AND sm.role_in_school = 'STUDENT'
        """;

    @Override
    public QueryPage<StudentProjectItem> findMine(UUID studentId, String executionStatus,
            String scoreStatus, String keyword, int page, int size) {

        StringBuilder where = new StringBuilder("WHERE " + OWNER_WHERE);
        List<Object> params = new ArrayList<>();
        params.add(studentId);

        if (executionStatus != null && !executionStatus.isBlank()) {
            where.append(" AND a.execution_status = ?");
            params.add(executionStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (a.title ILIKE ? OR cp.name ILIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }

        // For scoreStatus, we filter on the LATERAL latest attempt
        String lateralJoin = scoreStatus != null && !scoreStatus.isBlank()
            ? """
              INNER JOIN LATERAL (
                SELECT sa.* FROM score_attempts sa
                WHERE sa.activity_project_id = app.activity_project_id AND sa.student_id = ?
                  AND sa.score_status = ?
                ORDER BY COALESCE(sa.submitted_at, sa.created_at) DESC, sa.id DESC LIMIT 1
              ) sa ON true
              """
            : """
              LEFT JOIN LATERAL (
                SELECT sa.* FROM score_attempts sa
                WHERE sa.activity_project_id = app.activity_project_id AND sa.student_id = ?
                ORDER BY COALESCE(sa.submitted_at, sa.created_at) DESC, sa.id DESC LIMIT 1
              ) sa ON true
              """;

        if (scoreStatus != null && !scoreStatus.isBlank()) {
            params.add(studentId);
            params.add(scoreStatus);
        } else {
            params.add(studentId);
        }

        // Count
        String countSql = "SELECT count(*) " + BASE_FROM + lateralJoin + " " + where;
        List<Object> countParams = new ArrayList<>();
        // lateral params come first
        if (scoreStatus != null && !scoreStatus.isBlank()) {
            countParams.add(studentId); countParams.add(scoreStatus);
        } else {
            countParams.add(studentId);
        }
        countParams.addAll(params.subList(1, params.size())); // skip first studentId (already in OWNER_WHERE)
        Long total = jdbc.queryForObject(countSql, Long.class, countParams.toArray());
        if (total == null || total == 0)
            return new QueryPage<>(List.of(), page, size, 0);

        // Data
        String sql = """
            SELECT app.activity_project_id AS activity_project_id,
                   a.id AS activity_id, a.title AS activity_title,
                   cp.id AS project_id, cp.name AS project_name, cp.category,
                   cp.score_storage_type, cp.comparison_direction, cp.score_unit,
                   cp.decimal_places,
                   (SELECT count(*) FROM score_attempts sa3
                    WHERE sa3.activity_project_id = app.activity_project_id AND sa3.student_id = ?) AS attempt_count,
                   sa.id AS latest_attempt_id, sa.score_status AS latest_attempt_status,
                   sa.score_value, sa.score_duration_ms, sa.score_grade,
                   EXISTS(SELECT 1 FROM score_attempts sa4
                          WHERE sa4.activity_project_id = app.activity_project_id
                            AND sa4.student_id = ? AND sa4.score_status = 'APPROVED') AS has_approved,
                   app.assigned_at
            """ + BASE_FROM + lateralJoin + " " + where + """
            ORDER BY a.start_time DESC NULLS LAST, app.assigned_at DESC, app.activity_project_id DESC
            LIMIT ? OFFSET ?
            """;

        List<Object> sqlParams = new ArrayList<>();
        sqlParams.add(studentId); // attempt_count
        sqlParams.add(studentId); // has_approved
        if (scoreStatus != null && !scoreStatus.isBlank()) {
            sqlParams.add(studentId); sqlParams.add(scoreStatus);
        } else {
            sqlParams.add(studentId);
        }
        sqlParams.addAll(params.subList(1, params.size()));
        sqlParams.add(size);
        sqlParams.add(page * size);

        var items = jdbc.queryForList(sql, sqlParams.toArray()).stream()
                .map(row -> new StudentProjectItem(
                        (UUID) row.get("activity_project_id"), (UUID) row.get("activity_id"),
                        (String) row.get("activity_title"), (UUID) row.get("project_id"),
                        (String) row.get("project_name"), (String) row.get("category"),
                        (String) row.get("score_storage_type"), (String) row.get("comparison_direction"),
                        (String) row.get("score_unit"), ((Number) row.get("attempt_count")).intValue(),
                        (UUID) row.get("latest_attempt_id"), (String) row.get("latest_attempt_status"),
                        ScoreDisplayFormatter.format(
                                (String) row.get("score_storage_type"), row.get("score_value"),
                                row.get("score_duration_ms") != null ? ((Number) row.get("score_duration_ms")).longValue() : null,
                                (String) row.get("score_grade"),
                                row.get("decimal_places") != null ? ((Number) row.get("decimal_places")).intValue() : null),
                        (Boolean) row.get("has_approved"),
                        row.get("assigned_at") != null ? ((Timestamp) row.get("assigned_at")).toInstant() : null))
                .toList();
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<StudentProjectDetail> findMineById(UUID studentId, UUID activityProjectId) {
        String sql = """
            SELECT app.activity_project_id AS activity_project_id,
                   a.id AS activity_id, a.title AS activity_title,
                   a.description AS activity_description,
                   a.start_time AS activity_start_time, a.end_time AS activity_end_time, a.location,
                   cp.id AS project_id, cp.name AS project_name, cp.category,
                   cp.description AS project_description, cp.rules_text,
                   cp.venue_requirements, cp.equipment_requirements,
                   cp.effective_score_rule, cp.allow_tie, cp.decimal_places, cp.grade_order,
                   cp.score_storage_type, cp.comparison_direction, cp.score_unit,
                   (SELECT count(*) FROM score_attempts sa3
                    WHERE sa3.activity_project_id = app.activity_project_id AND sa3.student_id = ?) AS attempt_count,
                   sa.id AS latest_attempt_id, sa.score_status AS latest_attempt_status,
                   sa.score_value, sa.score_duration_ms, sa.score_grade,
                   EXISTS(SELECT 1 FROM score_attempts sa4
                          WHERE sa4.activity_project_id = app.activity_project_id
                            AND sa4.student_id = ? AND sa4.score_status = 'APPROVED') AS has_approved,
                   app.assigned_at
            FROM activity_project_participants app
            JOIN activity_projects ap2 ON app.activity_project_id = ap2.id
            JOIN activity_participants ap3 ON app.activity_participant_id = ap3.id
            JOIN school_memberships sm ON ap3.student_membership_id = sm.id
            JOIN activities a ON ap2.activity_id = a.id
            JOIN challenge_projects cp ON ap2.project_id = cp.id
            LEFT JOIN LATERAL (
              SELECT sa.* FROM score_attempts sa
              WHERE sa.activity_project_id = app.activity_project_id AND sa.student_id = ?
              ORDER BY COALESCE(sa.submitted_at, sa.created_at) DESC, sa.id DESC LIMIT 1
            ) sa ON true
            WHERE app.activity_project_id = ? AND sm.user_id = ? AND sm.status = 'ACTIVE'
              AND sm.role_in_school = 'STUDENT'
            """;

        var rows = jdbc.queryForList(sql, studentId, studentId, studentId, activityProjectId, studentId);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        int dp = row.get("decimal_places") != null ? ((Number) row.get("decimal_places")).intValue() : 0;

        return Optional.of(new StudentProjectDetail(
                (UUID) row.get("activity_project_id"), (UUID) row.get("activity_id"),
                (String) row.get("activity_title"), (UUID) row.get("project_id"),
                (String) row.get("project_name"), (String) row.get("category"),
                (String) row.get("score_storage_type"), (String) row.get("comparison_direction"),
                (String) row.get("score_unit"), ((Number) row.get("attempt_count")).intValue(),
                (UUID) row.get("latest_attempt_id"), (String) row.get("latest_attempt_status"),
                ScoreDisplayFormatter.format((String) row.get("score_storage_type"), row.get("score_value"),
                        row.get("score_duration_ms") != null ? ((Number) row.get("score_duration_ms")).longValue() : null,
                        (String) row.get("score_grade"), dp),
                (Boolean) row.get("has_approved"),
                row.get("assigned_at") != null ? ((Timestamp) row.get("assigned_at")).toInstant() : null,
                (String) row.get("activity_description"),
                toInstant(row.get("activity_start_time")), toInstant(row.get("activity_end_time")),
                (String) row.get("location"), (String) row.get("project_description"),
                (String) row.get("rules_text"), (String) row.get("venue_requirements"),
                (String) row.get("equipment_requirements"), (String) row.get("effective_score_rule"),
                (Boolean) row.get("allow_tie"), dp, (String) row.get("grade_order")));
    }

    private static java.time.Instant toInstant(Object o) {
        return o instanceof Timestamp ts ? ts.toInstant() : null;
    }
}
