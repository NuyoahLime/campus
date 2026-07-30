package com.campusguinness.score.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.ScoreDisplayFormatter;
import com.campusguinness.score.application.query.model.ScoreEntryParticipantOption;
import com.campusguinness.score.application.query.model.ScoreEntryProjectOption;
import com.campusguinness.score.application.query.port.SchoolAdminScoreEntryQueryPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolAdminScoreEntryQueryAdapter implements SchoolAdminScoreEntryQueryPort {
    private static final String PROJECT_FROM_AND_WHERE = """
            FROM activity_projects ap
            JOIN activities a ON a.id = ap.activity_id
            JOIN challenge_projects cp ON cp.id = ap.project_id
            WHERE a.school_id = :schoolId
              AND a.execution_status NOT IN ('ENDED', 'CANCELLED')
              AND (CAST(:keyword AS text) IS NULL
                   OR LOWER(a.title) LIKE :keyword ESCAPE '\\'
                   OR LOWER(cp.name) LIKE :keyword ESCAPE '\\')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    SchoolAdminScoreEntryQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<ScoreEntryProjectOption> findProjectOptions(
            UUID schoolId, String keyword, int page, int size) {
        var params = pageParameters(schoolId, keyword, page, size);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + PROJECT_FROM_AND_WHERE, params, Long.class);
        List<ScoreEntryProjectOption> items = jdbc.query("""
                SELECT ap.id AS activity_project_id, a.id AS activity_id,
                       a.title AS activity_title, a.execution_status,
                       cp.id AS project_id, cp.name AS project_name,
                       cp.score_storage_type, cp.score_unit, cp.decimal_places,
                       cp.grade_order, cp.comparison_direction, cp.effective_score_rule
                """ + PROJECT_FROM_AND_WHERE + """
                ORDER BY a.updated_at DESC, a.title ASC, cp.name ASC, ap.id ASC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> new ScoreEntryProjectOption(
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getString("execution_status"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getString("score_storage_type"),
                rs.getString("score_unit"),
                nullableInteger(rs, "decimal_places"),
                rs.getString("grade_order"),
                rs.getString("comparison_direction"),
                rs.getString("effective_score_rule")));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public QueryPage<ScoreEntryParticipantOption> findParticipantOptions(
            UUID schoolId,
            UUID activityProjectId,
            String keyword,
            int page,
            int size) {
        var params = pageParameters(schoolId, keyword, page, size)
                .addValue("activityProjectId", activityProjectId);
        String participantJoins = """
                FROM activity_project_participants app
                JOIN activity_projects ap ON ap.id = app.activity_project_id
                JOIN activities a ON a.id = ap.activity_id
                JOIN challenge_projects cp ON cp.id = ap.project_id
                JOIN activity_participants participant
                  ON participant.id = app.activity_participant_id
                 AND participant.activity_id = a.id
                JOIN school_memberships sm
                  ON sm.id = participant.student_membership_id
                 AND sm.school_id = :schoolId
                 AND sm.role_in_school = 'STUDENT'
                 AND sm.status = 'ACTIVE'
                JOIN users student ON student.id = sm.user_id
                LEFT JOIN student_profiles profile ON profile.membership_id = sm.id
                """;
        String participantWhere = """
                WHERE app.activity_project_id = :activityProjectId
                  AND a.school_id = :schoolId
                  AND (CAST(:keyword AS text) IS NULL
                       OR LOWER(student.username) LIKE :keyword ESCAPE '\\'
                       OR LOWER(COALESCE(profile.student_number, '')) LIKE :keyword ESCAPE '\\')
                """;
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) " + participantJoins + participantWhere,
                params, Long.class);
        List<ScoreEntryParticipantOption> items = jdbc.query("""
                SELECT sm.user_id AS student_id, student.username AS display_name,
                       profile.student_number, profile.grade, profile.class_name,
                       COUNT(sa.id) AS attempt_count,
                       MAX(sa.attempt_number) AS latest_attempt_number,
                       (ARRAY_AGG(sa.score_status ORDER BY sa.attempt_number DESC, sa.id DESC)
                         FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_attempt_status,
                       (ARRAY_AGG(sa.score_storage_type ORDER BY sa.attempt_number DESC, sa.id DESC)
                         FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_storage_type,
                       (ARRAY_AGG(sa.score_value ORDER BY sa.attempt_number DESC, sa.id DESC)
                         FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_score_value,
                       (ARRAY_AGG(sa.score_duration_ms ORDER BY sa.attempt_number DESC, sa.id DESC)
                         FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_duration_ms,
                       (ARRAY_AGG(sa.score_grade ORDER BY sa.attempt_number DESC, sa.id DESC)
                         FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_grade,
                       cp.decimal_places
                """ + participantJoins + """
                LEFT JOIN score_attempts sa
                  ON sa.activity_project_id = app.activity_project_id
                 AND sa.student_id = sm.user_id
                """ + participantWhere + """
                GROUP BY sm.user_id, student.username, profile.student_number,
                         profile.grade, profile.class_name, participant.created_at,
                         participant.id, cp.decimal_places
                ORDER BY participant.created_at DESC, participant.id DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> toParticipantOption(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    private static ScoreEntryParticipantOption toParticipantOption(ResultSet rs)
            throws SQLException {
        String storageType = rs.getString("latest_storage_type");
        String latestDisplay = storageType == null ? null : ScoreDisplayFormatter.format(
                storageType,
                rs.getBigDecimal("latest_score_value"),
                nullableLong(rs, "latest_duration_ms"),
                rs.getString("latest_grade"),
                nullableInteger(rs, "decimal_places"));
        return new ScoreEntryParticipantOption(
                rs.getObject("student_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("student_number"),
                rs.getString("grade"),
                rs.getString("class_name"),
                rs.getLong("attempt_count"),
                nullableInteger(rs, "latest_attempt_number"),
                rs.getString("latest_attempt_status"),
                latestDisplay);
    }

    private static MapSqlParameterSource pageParameters(
            UUID schoolId, String keyword, int page, int size) {
        String keywordPattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + escapeLike(keyword.trim().toLowerCase()) + "%";
        return new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("keyword", keywordPattern)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
