package com.campusguinness.score.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.StudentScoreDetailResult;
import com.campusguinness.score.application.query.model.StudentScoreListResult;
import com.campusguinness.score.application.query.port.StudentScoreQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class StudentScoreQueryAdapter implements StudentScoreQueryPort {
    private static final String VISIBLE_STATUS = "APPROVED";

    private final JdbcTemplate jdbc;

    StudentScoreQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<StudentScoreListResult> findVisibleByStudent(UUID studentId, UUID schoolId, int page, int size) {
        String from = " FROM score_attempts sa"
                + " JOIN activity_projects ap ON ap.id = sa.activity_project_id"
                + " JOIN activities a ON a.id = ap.activity_id"
                + " JOIN challenge_projects p ON p.id = ap.project_id"
                + " JOIN project_rule_versions rv ON rv.id = ap.rule_version_id AND rv.project_id = ap.project_id"
                + " WHERE sa.student_id = ? AND sa.school_id = ? AND sa.score_status = ?";
        String select = "SELECT sa.id, sa.activity_project_id, ap.activity_id, a.title AS activity_name,"
                + " p.name AS project_name, sa.attempt_number, sa.score_storage_type, sa.score_value,"
                + " sa.score_duration_ms, sa.score_grade, rv.score_unit, sa.score_business_time, sa.score_status"
                + from + " ORDER BY sa.score_business_time DESC NULLS LAST, sa.created_at DESC, sa.id DESC LIMIT ? OFFSET ?";
        List<StudentScoreListResult> items = jdbc.query(select, this::mapList,
                studentId, schoolId, VISIBLE_STATUS, size, page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from,
                Long.class, studentId, schoolId, VISIBLE_STATUS);
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<StudentScoreDetailResult> findVisibleById(UUID scoreAttemptId, UUID studentId, UUID schoolId) {
        String sql = "SELECT sa.id, sa.activity_project_id, ap.activity_id, a.title AS activity_name,"
                + " p.name AS project_name, sa.attempt_number, sa.score_storage_type, sa.score_value,"
                + " sa.score_duration_ms, sa.score_grade, rv.score_unit, sa.score_business_time,"
                + " sa.score_status, ap.rule_version_id, rv.version_number, rv.rules_text"
                + " FROM score_attempts sa"
                + " JOIN activity_projects ap ON ap.id = sa.activity_project_id"
                + " JOIN activities a ON a.id = ap.activity_id"
                + " JOIN challenge_projects p ON p.id = ap.project_id"
                + " JOIN project_rule_versions rv ON rv.id = ap.rule_version_id AND rv.project_id = ap.project_id"
                + " WHERE sa.id = ? AND sa.student_id = ? AND sa.school_id = ? AND sa.score_status = ?";
        return jdbc.query(sql, this::mapDetail, scoreAttemptId, studentId, schoolId, VISIBLE_STATUS)
                .stream().findFirst();
    }

    private StudentScoreListResult mapList(ResultSet rs, int row) throws SQLException {
        return new StudentScoreListResult(
                rs.getObject("id", UUID.class),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_name"),
                rs.getString("project_name"),
                rs.getInt("attempt_number"),
                rs.getString("score_storage_type"),
                scoreValue(rs),
                rs.getString("score_unit"),
                instant(rs, "score_business_time"),
                rs.getString("score_status"));
    }

    private StudentScoreDetailResult mapDetail(ResultSet rs, int row) throws SQLException {
        return new StudentScoreDetailResult(
                rs.getObject("id", UUID.class),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_name"),
                rs.getString("project_name"),
                rs.getInt("attempt_number"),
                rs.getString("score_storage_type"),
                scoreValue(rs),
                rs.getString("score_unit"),
                instant(rs, "score_business_time"),
                rs.getString("score_status"),
                rs.getObject("rule_version_id", UUID.class),
                rs.getInt("version_number"),
                rs.getString("rules_text"));
    }

    private String scoreValue(ResultSet rs) throws SQLException {
        return switch (rs.getString("score_storage_type")) {
            case "DURATION" -> String.valueOf(rs.getLong("score_duration_ms"));
            case "GRADE" -> rs.getString("score_grade");
            default -> {
                BigDecimal value = rs.getBigDecimal("score_value");
                yield value == null ? null : value.stripTrailingZeros().toPlainString();
            }
        };
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
