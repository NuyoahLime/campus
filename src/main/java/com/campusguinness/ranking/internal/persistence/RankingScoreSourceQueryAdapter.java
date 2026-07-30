package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.application.exception.RankingDataConflictException;
import com.campusguinness.ranking.application.query.model.RankingScoreSource;
import com.campusguinness.ranking.application.query.port.RankingScoreSourceQueryPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class RankingScoreSourceQueryAdapter implements RankingScoreSourceQueryPort {

    private final NamedParameterJdbcTemplate jdbc;

    RankingScoreSourceQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RankingScoreSource> findCurrentEffectiveApprovedSources(
            UUID schoolId, UUID activityProjectId) {
        return querySources(schoolId, activityProjectId, false);
    }

    @Override
    @Transactional
    public List<RankingScoreSource> lockCurrentEffectiveApprovedSources(
            UUID schoolId, UUID activityProjectId) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("activityProjectId", activityProjectId);
        jdbc.query("""
                SELECT ap.id
                FROM activity_projects ap
                JOIN activities activity ON activity.id = ap.activity_id
                JOIN challenge_projects project ON project.id = ap.project_id
                WHERE ap.id = :activityProjectId
                  AND activity.school_id = :schoolId
                FOR UPDATE OF ap, activity, project
                """, params, (rs, rowNumber) -> rs.getObject(1));
        jdbc.query("""
                SELECT score.id
                FROM score_attempts score
                WHERE score.activity_project_id = :activityProjectId
                  AND score.school_id = :schoolId
                ORDER BY score.id
                FOR UPDATE OF score
                """, params, (rs, rowNumber) -> rs.getObject(1));
        return querySources(schoolId, activityProjectId, false);
    }

    private List<RankingScoreSource> querySources(
            UUID schoolId, UUID activityProjectId, boolean lock) {
        var params = new MapSqlParameterSource()
                .addValue("schoolId", schoolId)
                .addValue("activityProjectId", activityProjectId);
        String sql = """
                SELECT sa.id AS score_attempt_id,
                       student.id AS student_id,
                       student.username AS student_display_name,
                       school.name AS school_name,
                       sa.score_storage_type,
                       sa.score_value,
                       sa.score_duration_ms,
                       sa.score_grade,
                       sa.score_business_time,
                       cp.current_rule_version_id,
                       cp.decimal_places
                FROM score_attempts sa
                JOIN activity_projects ap
                  ON ap.id = sa.activity_project_id
                JOIN activities a
                  ON a.id = ap.activity_id
                JOIN challenge_projects cp
                  ON cp.id = ap.project_id
                JOIN schools school
                  ON school.id = a.school_id
                JOIN users student
                  ON student.id = sa.student_id
                JOIN school_memberships student_membership
                  ON student_membership.user_id = student.id
                 AND student_membership.school_id = a.school_id
                 AND student_membership.role_in_school = 'STUDENT'
                 AND student_membership.status = 'ACTIVE'
                JOIN activity_participants participant
                  ON participant.activity_id = a.id
                 AND participant.student_membership_id = student_membership.id
                JOIN activity_project_participants app
                  ON app.activity_project_id = ap.id
                 AND app.activity_participant_id = participant.id
                WHERE sa.activity_project_id = :activityProjectId
                  AND sa.school_id = :schoolId
                  AND a.school_id = :schoolId
                  AND sa.score_status = 'APPROVED'
                  AND sa.is_current_effective = true
                ORDER BY student.id ASC, sa.id ASC
                """ + (lock ? " FOR SHARE OF sa" : "");
        List<RankingScoreSource> sources = jdbc.query(
                sql, params, (rs, rowNumber) -> mapSource(rs));
        assertUniqueStudents(sources);
        return List.copyOf(sources);
    }

    private static RankingScoreSource mapSource(ResultSet rs)
            throws SQLException {
        Timestamp businessTime = rs.getTimestamp("score_business_time");
        return new RankingScoreSource(
                rs.getObject("score_attempt_id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getString("student_display_name"),
                rs.getString("school_name"),
                rs.getString("score_storage_type"),
                rs.getBigDecimal("score_value"),
                nullableLong(rs, "score_duration_ms"),
                rs.getString("score_grade"),
                businessTime == null ? null : businessTime.toInstant(),
                rs.getObject("current_rule_version_id", UUID.class),
                nullableInteger(rs, "decimal_places"));
    }

    private static void assertUniqueStudents(List<RankingScoreSource> sources) {
        Set<UUID> students = new HashSet<>();
        for (RankingScoreSource source : sources) {
            if (!students.add(source.studentId())) {
                throw new RankingDataConflictException(
                        "Multiple current effective scores exist for the same student");
            }
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column)
            throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
