package com.campusguinness.score.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.ScoreDisplayFormatter;
import com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptDetail;
import com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptItem;
import com.campusguinness.score.application.query.model.ScoreReviewHistoryItem;
import com.campusguinness.score.application.query.port.SchoolAdminScoreQueryPort;
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
class SchoolAdminScoreQueryAdapter implements SchoolAdminScoreQueryPort {
    private static final String FROM_AND_WHERE = """
            FROM score_attempts sa
            JOIN activity_projects ap ON ap.id = sa.activity_project_id
            JOIN activities a ON a.id = ap.activity_id
            JOIN challenge_projects cp ON cp.id = ap.project_id
            JOIN users student ON student.id = sa.student_id
            JOIN users entrant ON entrant.id = sa.entered_by
            WHERE sa.school_id = :schoolId
              AND a.school_id = :schoolId
              AND sa.score_status = :status
              AND (CAST(:activityId AS uuid) IS NULL OR a.id = CAST(:activityId AS uuid))
              AND (CAST(:projectId AS uuid) IS NULL OR cp.id = CAST(:projectId AS uuid))
              AND (CAST(:keyword AS text) IS NULL
                   OR LOWER(student.username) LIKE :keyword ESCAPE '\\'
                   OR LOWER(entrant.username) LIKE :keyword ESCAPE '\\'
                   OR LOWER(a.title) LIKE :keyword ESCAPE '\\'
                   OR LOWER(cp.name) LIKE :keyword ESCAPE '\\')
            """;

    private static final String SELECT_FIELDS = """
            SELECT sa.id AS attempt_id, sa.school_id, a.id AS activity_id, a.title AS activity_title,
                   ap.id AS activity_project_id, cp.id AS project_id, cp.name AS project_name,
                   sa.student_id, student.username AS student_name, sa.attempt_number,
                   sa.score_storage_type, sa.score_value, sa.score_duration_ms, sa.score_grade,
                   cp.score_unit, sa.score_business_time, sa.time_source, sa.score_status,
                   sa.is_current_effective, sa.entered_by, entrant.username AS entered_by_name,
                   sa.submitted_at, sa.created_at, cp.effective_score_rule,
                   cp.comparison_direction, cp.decimal_places, cp.grade_order, cp.allow_tie
            """;

    private final NamedParameterJdbcTemplate jdbc;

    SchoolAdminScoreQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<SchoolAdminScoreAttemptItem> findBySchool(
            UUID schoolId, String status, UUID activityId, UUID projectId,
            String keyword, int page, int size) {
        var params = parameters(schoolId, status, activityId, projectId, keyword)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM_AND_WHERE, params, Long.class);
        List<SchoolAdminScoreAttemptItem> items = jdbc.query(
                SELECT_FIELDS + FROM_AND_WHERE + """
                        ORDER BY sa.submitted_at DESC NULLS LAST, sa.created_at DESC, sa.id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                (rs, rowNum) -> toItem(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<SchoolAdminScoreAttemptDetail> findDetail(UUID schoolId, UUID attemptId) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("attemptId", attemptId);
        DetailValues values = jdbc.query(
                SELECT_FIELDS + """
                        FROM score_attempts sa
                        JOIN activity_projects ap ON ap.id = sa.activity_project_id
                        JOIN activities a ON a.id = ap.activity_id
                        JOIN challenge_projects cp ON cp.id = ap.project_id
                        JOIN users student ON student.id = sa.student_id
                        JOIN users entrant ON entrant.id = sa.entered_by
                        WHERE sa.id = :attemptId
                          AND sa.school_id = :schoolId
                          AND a.school_id = :schoolId
                        """,
                params,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    SchoolAdminScoreAttemptItem item = toItem(rs);
                    BigDecimal rawValue = rs.getBigDecimal("score_value");
                    Long integerValue = "INTEGER".equals(item.scoreStorageType()) && rawValue != null
                            ? rawValue.longValueExact() : null;
                    BigDecimal decimalValue = "DECIMAL".equals(item.scoreStorageType())
                            ? rawValue : null;
                    return new DetailValues(
                            item, integerValue, decimalValue,
                            nullableLong(rs, "score_duration_ms"), rs.getString("score_grade"),
                            nullableInteger(rs, "decimal_places"), rs.getString("grade_order"),
                            rs.getBoolean("allow_tie"));
                });
        if (values == null) {
            return Optional.empty();
        }
        SchoolAdminScoreAttemptItem item = values.item();
        return Optional.of(new SchoolAdminScoreAttemptDetail(
                item.attemptId(), item.schoolId(), item.activityId(), item.activityTitle(),
                item.activityProjectId(), item.projectId(), item.projectName(),
                item.studentId(), item.studentName(), item.attemptNumber(),
                item.scoreStorageType(), item.displayValue(), item.scoreUnit(),
                item.scoreBusinessTime(), item.timeSource(), item.status(),
                item.currentEffective(), item.enteredBy(), item.enteredByName(),
                item.submittedAt(), item.createdAt(), item.effectiveScoreRule(),
                item.comparisonDirection(), values.integerValue(), values.decimalValue(),
                values.durationMs(), values.grade(), values.decimalPlaces(), values.gradeOrder(),
                values.allowTie(), findHistory(attemptId)));
    }

    private List<ScoreReviewHistoryItem> findHistory(UUID attemptId) {
        return jdbc.query("""
                SELECT srr.id, srr.reviewer_id, reviewer.username AS reviewer_name,
                       srr.review_result, srr.review_comment, srr.reject_reason, srr.reviewed_at
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

    private static SchoolAdminScoreAttemptItem toItem(ResultSet rs) throws SQLException {
        String storageType = rs.getString("score_storage_type");
        BigDecimal scoreValue = rs.getBigDecimal("score_value");
        Long durationMs = nullableLong(rs, "score_duration_ms");
        Integer decimalPlaces = nullableInteger(rs, "decimal_places");
        return new SchoolAdminScoreAttemptItem(
                rs.getObject("attempt_id", UUID.class),
                rs.getObject("school_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_name"),
                rs.getInt("attempt_number"),
                storageType,
                ScoreDisplayFormatter.format(storageType, scoreValue, durationMs,
                        rs.getString("score_grade"), decimalPlaces),
                rs.getString("score_unit"),
                instant(rs, "score_business_time"),
                rs.getString("time_source"),
                rs.getString("score_status"),
                rs.getBoolean("is_current_effective"),
                rs.getObject("entered_by", UUID.class),
                rs.getString("entered_by_name"),
                instant(rs, "submitted_at"),
                instant(rs, "created_at"),
                rs.getString("effective_score_rule"),
                rs.getString("comparison_direction"));
    }

    private static MapSqlParameterSource parameters(
            UUID schoolId, String status, UUID activityId, UUID projectId, String keyword) {
        String keywordPattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + escapeLike(keyword.toLowerCase()) + "%";
        return new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("status", status)
                .addValue("activityId", activityId)
                .addValue("projectId", projectId)
                .addValue("keyword", keywordPattern);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record DetailValues(
            SchoolAdminScoreAttemptItem item,
            Long integerValue,
            BigDecimal decimalValue,
            Long durationMs,
            String grade,
            Integer decimalPlaces,
            String gradeOrder,
            boolean allowTie) {
    }
}
