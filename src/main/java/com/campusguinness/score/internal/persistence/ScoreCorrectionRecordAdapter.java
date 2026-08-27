package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ScoreCorrectionRecordPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ScoreCorrectionRecordAdapter implements ScoreCorrectionRecordPort {
    private final JdbcTemplate jdbc;

    ScoreCorrectionRecordAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(UUID originalScoreId, UUID newScoreId, String reason, UUID correctedBy) {
        jdbc.update("""
                INSERT INTO score_correction_records(
                    original_score_id, new_score_id, correction_reason, corrected_by)
                VALUES (?, ?, ?, ?)
                """, originalScoreId, newScoreId, reason, correctedBy);
    }
}
