package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.query.model.ScoreReviewHistoryEntry;
import com.campusguinness.score.application.query.port.ScoreReviewHistoryQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class ScoreReviewHistoryQueryAdapter implements ScoreReviewHistoryQueryPort {
    private final JdbcTemplate jdbc;

    ScoreReviewHistoryQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ScoreReviewHistoryEntry> findByScoreAttemptId(UUID scoreAttemptId) {
        return jdbc.query("""
                SELECT srr.id, srr.review_result, srr.reviewer_id, u.username,
                       srr.review_comment,
                       CASE WHEN srr.review_result = 'REJECTED' THEN srr.reject_reason ELSE NULL END
                           AS reject_reason,
                       srr.reviewed_at
                FROM score_review_records srr
                JOIN users u ON u.id = srr.reviewer_id
                WHERE srr.score_attempt_id = ?
                ORDER BY srr.reviewed_at ASC, srr.id ASC
                """, this::map, scoreAttemptId);
    }

    private ScoreReviewHistoryEntry map(ResultSet rs, int row) throws SQLException {
        return new ScoreReviewHistoryEntry(
                rs.getObject("id", UUID.class),
                rs.getString("review_result"),
                rs.getObject("reviewer_id", UUID.class),
                rs.getString("username"),
                rs.getString("review_comment"),
                rs.getString("reject_reason"),
                rs.getTimestamp("reviewed_at").toInstant());
    }
}
