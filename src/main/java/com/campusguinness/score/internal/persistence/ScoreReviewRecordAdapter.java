package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ScoreReviewRecordPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ScoreReviewRecordAdapter implements ScoreReviewRecordPort {
    private final JdbcTemplate jdbc;

    ScoreReviewRecordAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(UUID scoreAttemptId, UUID reviewerId, String result, String reason) {
        jdbc.update("""
                INSERT INTO score_review_records(
                    score_attempt_id, reviewer_id, review_result, review_comment, reject_reason)
                VALUES (?, ?, ?, ?, ?)
                """, scoreAttemptId, reviewerId, result, null, "REJECTED".equals(result) ? reason : null);
    }
}
