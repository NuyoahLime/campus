package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.exception.ActivityParticipantAlreadyAssignedException;
import com.campusguinness.activity.application.port.ActivityParticipantPort;
import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.model.ActivityParticipantResult;
import com.campusguinness.activity.application.query.model.ActivityProjectResult;
import com.campusguinness.activity.internal.domain.ActivityParticipant;
import com.campusguinness.project.application.query.model.QueryPage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ActivityParticipantQueryAdapter implements ActivityParticipantPort {
    private static final List<String> STUDENT_VISIBLE_STATUSES = List.of("PUBLISHED", "IN_PROGRESS", "ENDED");
    private final JdbcTemplate jdbc;

    ActivityParticipantQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findActivitySchool(UUID activityId) {
        return jdbc.query("SELECT school_id FROM activities WHERE id = ?",
                rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty(), activityId);
    }

    @Override
    public Optional<UUID> findActiveStudentMembership(UUID studentId, UUID schoolId) {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM school_memberships
                WHERE user_id = ? AND school_id = ? AND status = 'ACTIVE'
                  AND role_in_school = 'STUDENT'
                ORDER BY started_at, id
                """, (rs, row) -> rs.getObject("id", UUID.class), studentId, schoolId);
        if (ids.size() != 1) return Optional.empty();
        return Optional.of(ids.getFirst());
    }

    @Override
    public List<ActivityParticipantResult> findParticipants(UUID activityId, UUID schoolId) {
        return jdbc.query("""
                SELECT sm.user_id AS student_id, u.username, sp.student_number,
                       sp.grade, sp.class_name, ap.created_at
                FROM activity_participants ap
                JOIN activities a ON a.id = ap.activity_id AND a.school_id = ?
                JOIN school_memberships sm ON sm.id = ap.student_membership_id
                    AND sm.school_id = a.school_id AND sm.role_in_school = 'STUDENT'
                    AND sm.status = 'ACTIVE'
                JOIN users u ON u.id = sm.user_id
                LEFT JOIN student_profiles sp ON sp.membership_id = sm.id
                WHERE ap.activity_id = ?
                ORDER BY LOWER(u.username), sm.user_id
                """, this::mapParticipant, schoolId, activityId);
    }

    @Override
    public List<ActivityParticipantResult> findCandidates(UUID activityId, UUID schoolId, String query) {
        StringBuilder sql = new StringBuilder("""
                SELECT sm.user_id AS student_id, u.username, sp.student_number,
                       sp.grade, sp.class_name, NULL AS created_at
                FROM school_memberships sm
                JOIN users u ON u.id = sm.user_id
                LEFT JOIN student_profiles sp ON sp.membership_id = sm.id
                WHERE sm.school_id = ? AND sm.status = 'ACTIVE' AND sm.role_in_school = 'STUDENT'
                  AND NOT EXISTS (
                      SELECT 1 FROM activity_participants ap
                      JOIN activities a ON a.id = ap.activity_id
                      WHERE ap.student_membership_id = sm.id AND a.id = ? AND a.school_id = ?
                  )
                """);
        List<Object> args = new ArrayList<>(List.of(schoolId, activityId, schoolId));
        if (query != null) {
            sql.append(" AND (LOWER(u.username) LIKE ? OR LOWER(COALESCE(sp.student_number, '')) LIKE ?)");
            String pattern = "%" + query.toLowerCase() + "%";
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY LOWER(u.username), sm.user_id LIMIT 100");
        return jdbc.query(sql.toString(), this::mapParticipant, args.toArray());
    }

    @Override
    public boolean exists(UUID activityId, UUID studentMembershipId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM activity_participants
                WHERE activity_id = ? AND student_membership_id = ?
                """, Integer.class, activityId, studentMembershipId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void save(ActivityParticipant participant) {
        try {
            jdbc.update("""
                    INSERT INTO activity_participants(id, activity_id, student_membership_id, created_at)
                    VALUES (?, ?, ?, ?)
                    """, participant.id(), participant.activityId(), participant.studentMembershipId(),
                    Timestamp.from(participant.createdAt()));
        } catch (DataIntegrityViolationException ex) {
            if (isParticipantDuplicate(ex)) {
                throw new ActivityParticipantAlreadyAssignedException();
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public boolean delete(UUID activityId, UUID studentMembershipId) {
        return jdbc.update("""
                DELETE FROM activity_participants
                WHERE activity_id = ? AND student_membership_id = ?
                """, activityId, studentMembershipId) == 1;
    }

    @Override
    public QueryPage<ActivityListResult> findAssignedActivities(UUID studentId, UUID schoolId, int page, int size) {
        String from = """
                FROM activity_participants ap
                JOIN school_memberships sm ON sm.id = ap.student_membership_id
                    AND sm.user_id = ? AND sm.school_id = ? AND sm.status = 'ACTIVE'
                    AND sm.role_in_school = 'STUDENT'
                JOIN activities a ON a.id = ap.activity_id AND a.school_id = sm.school_id
                    AND a.execution_status IN ('PUBLISHED', 'IN_PROGRESS', 'ENDED')
                JOIN schools s ON s.id = a.school_id
                """;
        String select = "SELECT a.id, a.school_id, a.title, a.start_time, a.end_time, a.location,"
                + " a.execution_status, s.name, s.region, a.description " + from
                + " ORDER BY a.start_time DESC NULLS LAST, a.id DESC LIMIT ? OFFSET ?";
        List<ActivityListResult> items = jdbc.query(select, this::mapActivityList,
                studentId, schoolId, size, page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, studentId, schoolId);
        return new QueryPage<>(items, page, size, total);
    }

    @Override
    public Optional<ActivityDetailResult> findAssignedActivity(UUID studentId, UUID schoolId, UUID activityId) {
        String sql = """
                SELECT a.id, a.school_id, s.name, s.region, a.title, a.description,
                       a.start_time, a.end_time, a.location, a.execution_status
                FROM activity_participants ap
                JOIN school_memberships sm ON sm.id = ap.student_membership_id
                    AND sm.user_id = ? AND sm.school_id = ? AND sm.status = 'ACTIVE'
                    AND sm.role_in_school = 'STUDENT'
                JOIN activities a ON a.id = ap.activity_id AND a.school_id = sm.school_id
                    AND a.execution_status IN ('PUBLISHED', 'IN_PROGRESS', 'ENDED')
                JOIN schools s ON s.id = a.school_id
                WHERE a.id = ?
                """;
        List<ActivityDetailResult> rows = jdbc.query(sql, this::mapActivityDetail,
                studentId, schoolId, activityId);
        if (rows.isEmpty()) return Optional.empty();
        ActivityDetailResult base = rows.getFirst();
        return Optional.of(new ActivityDetailResult(base.id(), base.schoolId(), base.schoolName(),
                base.schoolRegion(), base.title(), base.description(), base.startTime(), base.endTime(),
                base.location(), base.executionStatus(), loadProjects(base.id())));
    }

    private ActivityParticipantResult mapParticipant(ResultSet rs, int row) throws SQLException {
        return new ActivityParticipantResult(
                rs.getObject("student_id", UUID.class),
                rs.getString("username"),
                rs.getString("student_number"),
                rs.getString("grade"),
                rs.getString("class_name"),
                instant(rs, "created_at"));
    }

    private ActivityListResult mapActivityList(ResultSet rs, int row) throws SQLException {
        return new ActivityListResult(rs.getObject("id", UUID.class),
                rs.getObject("school_id", UUID.class), rs.getString("title"),
                instant(rs, "start_time"), instant(rs, "end_time"), rs.getString("location"),
                rs.getString("execution_status"), rs.getString("name"), rs.getString("region"),
                rs.getString("description"));
    }

    private ActivityDetailResult mapActivityDetail(ResultSet rs, int row) throws SQLException {
        return new ActivityDetailResult(rs.getObject("id", UUID.class),
                rs.getObject("school_id", UUID.class), rs.getString("name"),
                rs.getString("region"), rs.getString("title"), rs.getString("description"),
                instant(rs, "start_time"), instant(rs, "end_time"), rs.getString("location"),
                rs.getString("execution_status"), List.of());
    }

    private List<ActivityProjectResult> loadProjects(UUID activityId) {
        return jdbc.query("""
                SELECT ap.project_id, p.name, p.category, ap.rule_version_id,
                       rv.version_number, rv.rules_text, rv.score_storage_type,
                       rv.score_indicator_type, rv.comparison_direction, rv.score_unit, rv.allow_tie
                FROM activity_projects ap
                JOIN challenge_projects p ON p.id = ap.project_id
                JOIN project_rule_versions rv ON rv.id = ap.rule_version_id
                WHERE ap.activity_id = ?
                ORDER BY ap.id
                """, (rs, row) -> new ActivityProjectResult(
                rs.getObject("project_id", UUID.class), rs.getString("name"),
                rs.getString("category"), rs.getObject("rule_version_id", UUID.class),
                rs.getInt("version_number"), rs.getString("rules_text"),
                rs.getString("score_storage_type"), rs.getString("score_indicator_type"),
                rs.getString("comparison_direction"), rs.getString("score_unit"),
                rs.getBoolean("allow_tie")), activityId);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean isParticipantDuplicate(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && containsConstraintName(sqlException)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsConstraintName(SQLException exception) {
        return exception.getMessage() != null
                && exception.getMessage().contains("uq_activity_participant");
    }
}
