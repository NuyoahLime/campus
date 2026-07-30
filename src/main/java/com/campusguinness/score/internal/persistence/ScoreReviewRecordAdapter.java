package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ScoreReviewRecordPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ScoreReviewRecordAdapter implements ScoreReviewRecordPort {
    private final JdbcTemplate jdbc;

    ScoreReviewRecordAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(ScoreReviewRecord record) {
        jdbc.update("""
                INSERT INTO score_review_records(
                  id, score_attempt_id, reviewer_id, review_result,
                  review_comment, reject_reason, reviewed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                record.id(), record.scoreAttemptId(), record.reviewerId(), record.reviewResult(),
                record.reviewComment(), record.rejectReason(),
                java.sql.Timestamp.from(record.reviewedAt()));
    }
}
