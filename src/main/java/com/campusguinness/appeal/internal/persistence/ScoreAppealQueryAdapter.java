package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.appeal.application.query.model.ScoreAppealDetailResult;
import com.campusguinness.appeal.application.query.model.ScoreAppealListResult;
import com.campusguinness.appeal.application.query.port.ScoreAppealQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
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
class ScoreAppealQueryAdapter implements ScoreAppealQueryPort {
    private final JdbcTemplate jdbc;

    ScoreAppealQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<ScoreAppealListResult> findByStudent(UUID studentId, UUID schoolId, int page, int size) {
        String from = baseFrom() + " WHERE appeal.student_id = ? AND appeal.school_id = ?";
        List<ScoreAppealListResult> items = jdbc.query(listSql(from), this::mapList,
                studentId, schoolId, size, page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, studentId, schoolId);
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<ScoreAppealDetailResult> findByIdAndStudent(UUID appealId, UUID studentId, UUID schoolId) {
        String from = baseFrom() + " WHERE appeal.id = ? AND appeal.student_id = ? AND appeal.school_id = ?";
        return jdbc.query(detailSql(from), this::mapDetail, appealId, studentId, schoolId).stream().findFirst();
    }

    @Override
    public QueryPage<ScoreAppealListResult> findBySchool(UUID schoolId, int page, int size) {
        String from = baseFrom() + " WHERE appeal.school_id = ?";
        List<ScoreAppealListResult> items = jdbc.query(listSql(from), this::mapList,
                schoolId, size, page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, schoolId);
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<ScoreAppealDetailResult> findByIdAndSchool(UUID appealId, UUID schoolId) {
        String from = baseFrom() + " WHERE appeal.id = ? AND appeal.school_id = ?";
        return jdbc.query(detailSql(from), this::mapDetail, appealId, schoolId).stream().findFirst();
    }

    private String baseFrom() {
        return " FROM score_appeals appeal"
                + " JOIN score_attempts score ON score.id = appeal.score_attempt_id"
                + " JOIN activity_projects ap ON ap.id = score.activity_project_id"
                + " JOIN activities activity ON activity.id = ap.activity_id"
                + " JOIN challenge_projects project ON project.id = ap.project_id"
                + " JOIN project_rule_versions rule_version ON rule_version.id = ap.rule_version_id"
                + " AND rule_version.project_id = ap.project_id";
    }

    private String listSql(String from) {
        return "SELECT appeal.id, appeal.score_attempt_id, activity.title AS activity_name,"
                + " project.name AS project_name, appeal.appeal_type, appeal.appeal_status,"
                + " appeal.created_at, appeal.updated_at"
                + from
                + " ORDER BY appeal.created_at DESC, appeal.id DESC LIMIT ? OFFSET ?";
    }

    private String detailSql(String from) {
        return "SELECT appeal.id, appeal.score_attempt_id, activity.title AS activity_name,"
                + " project.name AS project_name, score.score_storage_type, score.score_value,"
                + " score.score_duration_ms, score.score_grade, rule_version.score_unit,"
                + " appeal.appeal_type, appeal.appeal_reason, appeal.appeal_status,"
                + " appeal.resolution, appeal.resolved_at, appeal.created_at, appeal.updated_at"
                + from;
    }

    private ScoreAppealListResult mapList(ResultSet rs, int row) throws SQLException {
        return new ScoreAppealListResult(
                rs.getObject("id", UUID.class),
                rs.getObject("score_attempt_id", UUID.class),
                rs.getString("activity_name"),
                rs.getString("project_name"),
                rs.getString("appeal_type"),
                rs.getString("appeal_status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private ScoreAppealDetailResult mapDetail(ResultSet rs, int row) throws SQLException {
        return new ScoreAppealDetailResult(
                rs.getObject("id", UUID.class),
                rs.getObject("score_attempt_id", UUID.class),
                rs.getString("activity_name"),
                rs.getString("project_name"),
                rs.getString("score_storage_type"),
                scoreValue(rs),
                rs.getString("score_unit"),
                rs.getString("appeal_type"),
                rs.getString("appeal_reason"),
                rs.getString("appeal_status"),
                rs.getString("resolution"),
                instant(rs, "resolved_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
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
