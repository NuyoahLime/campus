package com.campusguinness.feedback.internal.persistence;

import com.campusguinness.feedback.application.query.model.FeedbackDetailResult;
import com.campusguinness.feedback.application.query.model.FeedbackListResult;
import com.campusguinness.feedback.application.query.port.FeedbackQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class FeedbackQueryAdapter implements FeedbackQueryPort {
    private final JdbcTemplate jdbc;

    FeedbackQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<FeedbackListResult> findByStudent(UUID submitterId, UUID schoolId, int page, int size) {
        String from = " FROM feedbacks WHERE submitter_id = ? AND school_id = ?";
        List<FeedbackListResult> items = jdbc.query(listSql(from), this::mapList,
                submitterId, schoolId, size, page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, submitterId, schoolId);
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<FeedbackDetailResult> findByIdAndStudent(UUID feedbackId, UUID submitterId, UUID schoolId) {
        String from = " FROM feedbacks WHERE id = ? AND submitter_id = ? AND school_id = ?";
        return jdbc.query(detailSql(from), this::mapDetail, feedbackId, submitterId, schoolId).stream().findFirst();
    }

    @Override
    public QueryPage<FeedbackListResult> findBySchool(UUID schoolId, int page, int size) {
        String from = " FROM feedbacks WHERE school_id = ?";
        List<FeedbackListResult> items = jdbc.query(listSql(from), this::mapList, schoolId, size, page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, schoolId);
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<FeedbackDetailResult> findByIdAndSchool(UUID feedbackId, UUID schoolId) {
        String from = " FROM feedbacks WHERE id = ? AND school_id = ?";
        return jdbc.query(detailSql(from), this::mapDetail, feedbackId, schoolId).stream().findFirst();
    }

    private String listSql(String from) {
        return "SELECT id, feedback_type, feedback_status, created_at, updated_at"
                + from + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";
    }

    private String detailSql(String from) {
        return "SELECT id, feedback_type, content, feedback_status, reply, close_reason, created_at, updated_at" + from;
    }

    private FeedbackListResult mapList(ResultSet rs, int row) throws SQLException {
        return new FeedbackListResult(
                rs.getObject("id", UUID.class),
                rs.getString("feedback_type"),
                rs.getString("feedback_status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private FeedbackDetailResult mapDetail(ResultSet rs, int row) throws SQLException {
        return new FeedbackDetailResult(
                rs.getObject("id", UUID.class),
                rs.getString("feedback_type"),
                rs.getString("content"),
                rs.getString("feedback_status"),
                rs.getString("reply"),
                rs.getString("close_reason"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
