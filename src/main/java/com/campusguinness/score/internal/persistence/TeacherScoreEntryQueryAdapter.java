package com.campusguinness.score.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.ScoreDisplayFormatter;
import com.campusguinness.score.application.query.model.ScoreReviewHistoryItem;
import com.campusguinness.score.application.query.model.TeacherScoreAttemptDetail;
import com.campusguinness.score.application.query.model.TeacherScoreAttemptItem;
import com.campusguinness.score.application.query.port.TeacherScoreEntryQueryPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class TeacherScoreEntryQueryAdapter implements TeacherScoreEntryQueryPort {
    private static final String SELECT_FIELDS = """
            SELECT sa.id AS attempt_id, ap.id AS activity_project_id,
                   a.id AS activity_id, a.title AS activity_title,
                   s.id AS school_id, s.name AS school_name,
                   cp.id AS project_id, cp.name AS project_name,
                   sa.student_id, student.username AS student_name,
                   sa.attempt_number, sa.score_storage_type, sa.score_value,
                   sa.score_duration_ms, sa.score_grade, cp.score_unit,
                   cp.decimal_places, cp.grade_order,
                   sa.score_business_time, sa.time_source, sa.score_status,
                   sa.submitted_at, sa.created_at, sa.updated_at,
                   sa.is_current_effective
            """;
    private static final String FROM_AND_WHERE = """
            FROM score_attempts sa
            JOIN activity_projects ap ON ap.id = sa.activity_project_id
            JOIN activities a ON a.id = ap.activity_id
            JOIN schools s ON s.id = a.school_id
            JOIN challenge_projects cp ON cp.id = ap.project_id
            JOIN users student ON student.id = sa.student_id
            WHERE sa.entered_by = :actorId
              AND sa.school_id = a.school_id
              AND EXISTS (
                SELECT 1
                FROM school_memberships teacher_membership
                WHERE teacher_membership.user_id = :actorId
                  AND teacher_membership.school_id = a.school_id
                  AND teacher_membership.role_in_school = 'TEACHER'
                  AND teacher_membership.status = 'ACTIVE'
              )
              AND (CAST(:status AS text) IS NULL OR sa.score_status = :status)
              AND (CAST(:activityProjectId AS uuid) IS NULL
                   OR sa.activity_project_id = CAST(:activityProjectId AS uuid))
              AND (CAST(:keyword AS text) IS NULL
                   OR LOWER(student.username) LIKE :keyword ESCAPE '\\'
                   OR LOWER(a.title) LIKE :keyword ESCAPE '\\'
                   OR LOWER(cp.name) LIKE :keyword ESCAPE '\\'
                   OR LOWER(s.name) LIKE :keyword ESCAPE '\\')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    TeacherScoreEntryQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<TeacherScoreAttemptItem> findMine(
            UUID actorId,
            String status,
            UUID activityProjectId,
            String keyword,
            int page,
            int size) {
        var params = parameters(actorId, status, activityProjectId, keyword)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + FROM_AND_WHERE, params, Long.class);
        List<TeacherScoreAttemptItem> items = jdbc.query(
                SELECT_FIELDS + FROM_AND_WHERE + """
                        ORDER BY sa.updated_at DESC, sa.created_at DESC, sa.id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                (rs, rowNum) -> toItem(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<TeacherScoreAttemptDetail> findDetail(
            UUID actorId,
            UUID attemptId) {
        var params = parameters(actorId, null, null, null)
                .addValue("attemptId", attemptId);
        TeacherScoreAttemptDetail detail = jdbc.query(
                SELECT_FIELDS + FROM_AND_WHERE + """
                        AND sa.id = :attemptId
                        """,
                params,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    TeacherScoreAttemptItem item = toItem(rs);
                    BigDecimal rawValue = rs.getBigDecimal("score_value");
                    Long integerValue = "INTEGER".equals(item.scoreStorageType())
                            && rawValue != null ? rawValue.longValueExact() : null;
                    BigDecimal decimalValue = "DECIMAL".equals(item.scoreStorageType())
                            ? rawValue : null;
                    return new TeacherScoreAttemptDetail(
                            item.attemptId(), item.activityProjectId(),
                            item.activityId(), item.activityTitle(),
                            item.schoolId(), item.schoolName(),
                            item.projectId(), item.projectName(),
                            item.studentId(), item.studentName(),
                            item.attemptNumber(), item.scoreStorageType(),
                            item.displayValue(), item.scoreUnit(),
                            integerValue, decimalValue,
                            nullableLong(rs, "score_duration_ms"),
                            rs.getString("score_grade"),
                            nullableInteger(rs, "decimal_places"),
                            rs.getString("grade_order"),
                            item.scoreBusinessTime(), item.timeSource(),
                            item.status(), item.submittedAt(), item.createdAt(),
                            item.updatedAt(), item.currentEffective(),
                            findHistory(attemptId));
                });
        return Optional.ofNullable(detail);
    }

    private List<ScoreReviewHistoryItem> findHistory(UUID attemptId) {
        return jdbc.query("""
                SELECT srr.id, srr.reviewer_id, reviewer.username AS reviewer_name,
                       srr.review_result, srr.review_comment, srr.reject_reason,
                       srr.reviewed_at
                FROM score_review_records srr
                JOIN users reviewer ON reviewer.id = srr.reviewer_id
                WHERE srr.score_attempt_id = :attemptId
                ORDER BY srr.reviewed_at DESC, srr.id DESC
                """,
                new MapSqlParameterSource("attemptId", attemptId),
                (rs, rowNum) -> new ScoreReviewHistoryItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("reviewer_id", UUID.class),
                        rs.getString("reviewer_name"),
                        rs.getString("review_result"),
                        rs.getString("review_comment"),
                        rs.getString("reject_reason"),
                        instant(rs, "reviewed_at")));
    }

    private static TeacherScoreAttemptItem toItem(ResultSet rs)
            throws SQLException {
        String storageType = rs.getString("score_storage_type");
        return new TeacherScoreAttemptItem(
                rs.getObject("attempt_id", UUID.class),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getObject("school_id", UUID.class),
                rs.getString("school_name"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_name"),
                rs.getInt("attempt_number"),
                storageType,
                ScoreDisplayFormatter.format(
                        storageType,
                        rs.getBigDecimal("score_value"),
                        nullableLong(rs, "score_duration_ms"),
                        rs.getString("score_grade"),
                        nullableInteger(rs, "decimal_places")),
                rs.getString("score_unit"),
                instant(rs, "score_business_time"),
                rs.getString("time_source"),
                rs.getString("score_status"),
                instant(rs, "submitted_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getBoolean("is_current_effective"));
    }

    private static MapSqlParameterSource parameters(
            UUID actorId,
            String status,
            UUID activityProjectId,
            String keyword) {
        return new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("status", status)
                .addValue("activityProjectId", activityProjectId)
                .addValue("keyword", keyword == null || keyword.isBlank()
                        ? null
                        : "%" + escapeLike(keyword.trim().toLowerCase()) + "%");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Instant instant(ResultSet rs, String column)
            throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column)
            throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column)
            throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
