package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.TeacherProjectParticipantItem;
import com.campusguinness.activity.application.query.model.TeacherResponsibleProjectDetail;
import com.campusguinness.activity.application.query.model.TeacherResponsibleProjectItem;
import com.campusguinness.activity.application.query.model.TeacherResponsibleTeacherItem;
import com.campusguinness.activity.application.query.port.TeacherResponsibleProjectQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.ScoreDisplayFormatter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class TeacherResponsibleProjectQueryAdapter
        implements TeacherResponsibleProjectQueryPort {
    private static final String PROJECT_CTES = """
            WITH scoped_projects AS (
              SELECT ap.id AS activity_project_id, ap.activity_id, ap.project_id,
                     a.title AS activity_title, a.description AS activity_description,
                     a.school_id, s.name AS school_name, a.execution_status,
                     a.start_time, a.end_time, a.location, a.updated_at,
                     cp.name AS project_name, cp.category,
                     cp.description AS project_description, cp.rules_text,
                     cp.venue_requirements, cp.equipment_requirements,
                     cp.score_storage_type, cp.score_unit, cp.decimal_places,
                     cp.grade_order, cp.comparison_direction,
                     cp.effective_score_rule, cp.allow_tie
              FROM responsible_teachers rt
              JOIN school_memberships teacher_membership
                ON teacher_membership.id = rt.teacher_membership_id
              JOIN activity_projects ap ON ap.id = rt.activity_project_id
              JOIN activities a ON a.id = ap.activity_id
              JOIN schools s ON s.id = a.school_id
              JOIN challenge_projects cp ON cp.id = ap.project_id
              WHERE teacher_membership.user_id = :actorId
                AND teacher_membership.school_id = a.school_id
                AND teacher_membership.role_in_school = 'TEACHER'
                AND teacher_membership.status = 'ACTIVE'
            ),
            participant_counts AS (
              SELECT app.activity_project_id, COUNT(*) AS participant_count
              FROM activity_project_participants app
              JOIN scoped_projects scoped
                ON scoped.activity_project_id = app.activity_project_id
              GROUP BY app.activity_project_id
            ),
            attempt_counts AS (
              SELECT sa.activity_project_id,
                     COUNT(*) AS entered_attempt_count,
                     COUNT(*) FILTER (
                       WHERE sa.score_status = 'PENDING_REVIEW'
                     ) AS pending_review_count,
                     COUNT(*) FILTER (
                       WHERE sa.score_status = 'REJECTED'
                     ) AS rejected_count
              FROM score_attempts sa
              JOIN scoped_projects scoped
                ON scoped.activity_project_id = sa.activity_project_id
              WHERE sa.entered_by = :actorId
              GROUP BY sa.activity_project_id
            )
            """;
    private static final String PROJECT_FROM_AND_WHERE = """
            FROM scoped_projects scoped
            LEFT JOIN participant_counts participants
              ON participants.activity_project_id = scoped.activity_project_id
            LEFT JOIN attempt_counts attempts
              ON attempts.activity_project_id = scoped.activity_project_id
            WHERE (CAST(:executionStatus AS text) IS NULL
                   OR scoped.execution_status = :executionStatus)
              AND (CAST(:keyword AS text) IS NULL
                   OR LOWER(scoped.activity_title) LIKE :keyword ESCAPE '\\'
                   OR LOWER(scoped.project_name) LIKE :keyword ESCAPE '\\'
                   OR LOWER(scoped.school_name) LIKE :keyword ESCAPE '\\')
            """;
    private static final String PROJECT_SELECT = """
            SELECT scoped.*,
                   COALESCE(participants.participant_count, 0) AS participant_count,
                   COALESCE(attempts.entered_attempt_count, 0) AS entered_attempt_count,
                   COALESCE(attempts.pending_review_count, 0) AS pending_review_count,
                   COALESCE(attempts.rejected_count, 0) AS rejected_count
            """;

    private static final String PARTICIPANT_CTES = """
            WITH scoped_project AS (
              SELECT ap.id AS activity_project_id, a.id AS activity_id,
                     a.school_id, cp.decimal_places
              FROM responsible_teachers rt
              JOIN school_memberships teacher_membership
                ON teacher_membership.id = rt.teacher_membership_id
              JOIN activity_projects ap ON ap.id = rt.activity_project_id
              JOIN activities a ON a.id = ap.activity_id
              JOIN challenge_projects cp ON cp.id = ap.project_id
              WHERE ap.id = :activityProjectId
                AND teacher_membership.user_id = :actorId
                AND teacher_membership.school_id = a.school_id
                AND teacher_membership.role_in_school = 'TEACHER'
                AND teacher_membership.status = 'ACTIVE'
            ),
            participant_rows AS (
              SELECT student_membership.user_id AS student_id,
                     student.username AS display_name,
                     profile.student_number, profile.grade, profile.class_name,
                     app.assigned_at,
                     COUNT(sa.id) AS attempt_count,
                     (ARRAY_AGG(sa.id ORDER BY sa.attempt_number DESC, sa.id DESC)
                       FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_attempt_id,
                     MAX(sa.attempt_number) AS latest_attempt_number,
                     (ARRAY_AGG(sa.score_status
                       ORDER BY sa.attempt_number DESC, sa.id DESC)
                       FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_attempt_status,
                     (ARRAY_AGG(sa.score_storage_type
                       ORDER BY sa.attempt_number DESC, sa.id DESC)
                       FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_storage_type,
                     (ARRAY_AGG(sa.score_value
                       ORDER BY sa.attempt_number DESC, sa.id DESC)
                       FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_score_value,
                     (ARRAY_AGG(sa.score_duration_ms
                       ORDER BY sa.attempt_number DESC, sa.id DESC)
                       FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_duration_ms,
                     (ARRAY_AGG(sa.score_grade
                       ORDER BY sa.attempt_number DESC, sa.id DESC)
                       FILTER (WHERE sa.id IS NOT NULL))[1] AS latest_grade,
                     BOOL_OR(sa.score_status = 'APPROVED') AS has_approved_score,
                     scoped.decimal_places
              FROM scoped_project scoped
              JOIN activity_project_participants app
                ON app.activity_project_id = scoped.activity_project_id
              JOIN activity_participants participant
                ON participant.id = app.activity_participant_id
               AND participant.activity_id = scoped.activity_id
              JOIN school_memberships student_membership
                ON student_membership.id = participant.student_membership_id
               AND student_membership.school_id = scoped.school_id
               AND student_membership.role_in_school = 'STUDENT'
               AND student_membership.status = 'ACTIVE'
              JOIN users student ON student.id = student_membership.user_id
              LEFT JOIN student_profiles profile
                ON profile.membership_id = student_membership.id
              LEFT JOIN score_attempts sa
                ON sa.activity_project_id = scoped.activity_project_id
               AND sa.student_id = student_membership.user_id
              GROUP BY student_membership.user_id, student.username,
                       profile.student_number, profile.grade, profile.class_name,
                       app.assigned_at, app.id, scoped.decimal_places
            )
            """;
    private static final String PARTICIPANT_WHERE = """
            WHERE (CAST(:keyword AS text) IS NULL
                   OR LOWER(display_name) LIKE :keyword ESCAPE '\\'
                   OR LOWER(COALESCE(student_number, '')) LIKE :keyword ESCAPE '\\')
              AND (CAST(:status AS text) IS NULL
                   OR (:status = 'NO_SCORE' AND attempt_count = 0)
                   OR (:status <> 'NO_SCORE' AND latest_attempt_status = :status))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    TeacherResponsibleProjectQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public QueryPage<TeacherResponsibleProjectItem> findResponsibleProjects(
            UUID actorId,
            String executionStatus,
            String keyword,
            int page,
            int size) {
        var params = projectParameters(
                actorId, executionStatus, keyword, page, size);
        Long total = jdbc.queryForObject(
                PROJECT_CTES + "SELECT COUNT(*) " + PROJECT_FROM_AND_WHERE,
                params,
                Long.class);
        List<TeacherResponsibleProjectItem> items = jdbc.query(
                PROJECT_CTES + PROJECT_SELECT + PROJECT_FROM_AND_WHERE + """
                        ORDER BY scoped.updated_at DESC, scoped.activity_title ASC,
                                 scoped.project_name ASC, scoped.activity_project_id ASC
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                (rs, rowNum) -> toProjectItem(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<TeacherResponsibleProjectDetail> findResponsibleProject(
            UUID actorId,
            UUID activityProjectId) {
        var params = projectParameters(actorId, null, null, 0, 1)
                .addValue("activityProjectId", activityProjectId);
        TeacherResponsibleProjectDetail detail = jdbc.query(
                PROJECT_CTES + PROJECT_SELECT + PROJECT_FROM_AND_WHERE + """
                        AND scoped.activity_project_id = :activityProjectId
                        """,
                params,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    TeacherResponsibleProjectItem item = toProjectItem(rs);
                    return new TeacherResponsibleProjectDetail(
                            item.activityProjectId(), item.activityId(),
                            item.activityTitle(), item.schoolId(), item.schoolName(),
                            item.executionStatus(), item.startTime(), item.endTime(),
                            item.location(), item.projectId(), item.projectName(),
                            item.category(), item.scoreStorageType(), item.scoreUnit(),
                            item.decimalPlaces(), item.gradeOrder(),
                            item.comparisonDirection(), item.effectiveScoreRule(),
                            item.participantCount(), item.enteredAttemptCount(),
                            item.pendingReviewCount(), item.rejectedCount(),
                            rs.getString("activity_description"),
                            rs.getString("project_description"),
                            rs.getString("rules_text"),
                            rs.getString("venue_requirements"),
                            rs.getString("equipment_requirements"),
                            rs.getBoolean("allow_tie"),
                            findResponsibleTeachers(activityProjectId));
                });
        return Optional.ofNullable(detail);
    }

    @Override
    public QueryPage<TeacherProjectParticipantItem> findProjectParticipants(
            UUID actorId,
            UUID activityProjectId,
            String keyword,
            String status,
            int page,
            int size) {
        var params = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("activityProjectId", activityProjectId)
                .addValue("keyword", keywordPattern(keyword))
                .addValue("status", status)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject(
                PARTICIPANT_CTES + "SELECT COUNT(*) FROM participant_rows "
                        + PARTICIPANT_WHERE,
                params,
                Long.class);
        List<TeacherProjectParticipantItem> items = jdbc.query(
                PARTICIPANT_CTES + "SELECT * FROM participant_rows "
                        + PARTICIPANT_WHERE + """
                        ORDER BY assigned_at DESC, display_name ASC, student_id ASC
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                (rs, rowNum) -> toParticipantItem(rs));
        return new QueryPage<>(items, page, size, total == null ? 0 : total);
    }

    private List<TeacherResponsibleTeacherItem> findResponsibleTeachers(
            UUID activityProjectId) {
        return jdbc.query("""
                SELECT teacher_membership.user_id, teacher.username,
                       profile.subject, profile.title
                FROM responsible_teachers rt
                JOIN school_memberships teacher_membership
                  ON teacher_membership.id = rt.teacher_membership_id
                JOIN users teacher ON teacher.id = teacher_membership.user_id
                LEFT JOIN teacher_profiles profile
                  ON profile.membership_id = teacher_membership.id
                WHERE rt.activity_project_id = :activityProjectId
                ORDER BY teacher.username ASC, teacher_membership.user_id ASC
                """,
                new MapSqlParameterSource(
                        "activityProjectId", activityProjectId),
                (rs, rowNum) -> new TeacherResponsibleTeacherItem(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("username"),
                        rs.getString("subject"),
                        rs.getString("title")));
    }

    private static TeacherResponsibleProjectItem toProjectItem(ResultSet rs)
            throws SQLException {
        return new TeacherResponsibleProjectItem(
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("activity_title"),
                rs.getObject("school_id", UUID.class),
                rs.getString("school_name"),
                rs.getString("execution_status"),
                instant(rs, "start_time"),
                instant(rs, "end_time"),
                rs.getString("location"),
                rs.getObject("project_id", UUID.class),
                rs.getString("project_name"),
                rs.getString("category"),
                rs.getString("score_storage_type"),
                rs.getString("score_unit"),
                nullableInteger(rs, "decimal_places"),
                rs.getString("grade_order"),
                rs.getString("comparison_direction"),
                rs.getString("effective_score_rule"),
                rs.getLong("participant_count"),
                rs.getLong("entered_attempt_count"),
                rs.getLong("pending_review_count"),
                rs.getLong("rejected_count"));
    }

    private static TeacherProjectParticipantItem toParticipantItem(ResultSet rs)
            throws SQLException {
        String storageType = rs.getString("latest_storage_type");
        BigDecimal numericValue = rs.getBigDecimal("latest_score_value");
        Long durationMs = nullableLong(rs, "latest_duration_ms");
        String displayValue = storageType == null ? null : ScoreDisplayFormatter.format(
                storageType,
                numericValue,
                durationMs,
                rs.getString("latest_grade"),
                nullableInteger(rs, "decimal_places"));
        return new TeacherProjectParticipantItem(
                rs.getObject("student_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("student_number"),
                rs.getString("grade"),
                rs.getString("class_name"),
                rs.getLong("attempt_count"),
                rs.getObject("latest_attempt_id", UUID.class),
                nullableInteger(rs, "latest_attempt_number"),
                rs.getString("latest_attempt_status"),
                displayValue,
                rs.getBoolean("has_approved_score"),
                instant(rs, "assigned_at"));
    }

    private static MapSqlParameterSource projectParameters(
            UUID actorId,
            String executionStatus,
            String keyword,
            int page,
            int size) {
        return new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("executionStatus", executionStatus)
                .addValue("keyword", keywordPattern(keyword))
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
    }

    private static String keywordPattern(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : "%" + escapeLike(keyword.trim().toLowerCase()) + "%";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
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
