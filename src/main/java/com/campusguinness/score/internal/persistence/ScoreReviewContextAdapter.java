package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.application.port.ScoreReviewContextPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class ScoreReviewContextAdapter implements ScoreReviewContextPort {
    private final NamedParameterJdbcTemplate jdbc;

    ScoreReviewContextAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReviewContext> findReviewContext(UUID attemptId, UUID actorSchoolId) {
        var rows = jdbc.query("""
                SELECT sa.id, sa.school_id, sa.activity_project_id, sa.student_id,
                       cp.score_storage_type, cp.effective_score_rule,
                       cp.comparison_direction, cp.grade_order
                FROM score_attempts sa
                JOIN activity_projects ap ON ap.id = sa.activity_project_id
                JOIN activities a ON a.id = ap.activity_id
                JOIN challenge_projects cp ON cp.id = ap.project_id
                JOIN school_memberships sm
                  ON sm.user_id = sa.student_id
                 AND sm.school_id = :schoolId
                 AND sm.role_in_school = 'STUDENT'
                 AND sm.status = 'ACTIVE'
                WHERE sa.id = :attemptId
                  AND sa.school_id = :schoolId
                  AND a.school_id = :schoolId
                """,
                new MapSqlParameterSource()
                        .addValue("attemptId", attemptId)
                        .addValue("schoolId", actorSchoolId),
                (rs, rowNum) -> new ReviewContext(
                        rs.getObject("id", UUID.class),
                        rs.getObject("school_id", UUID.class),
                        rs.getObject("activity_project_id", UUID.class),
                        rs.getObject("student_id", UUID.class),
                        rs.getString("score_storage_type"),
                        rs.getString("effective_score_rule"),
                        rs.getString("comparison_direction"),
                        rs.getString("grade_order")));
        return rows.stream().findFirst();
    }
}
