package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
class ResponsibleTeacherAdapter implements ResponsibleTeacherPort {
    private final ResponsibleTeacherJpaRepository jpa;
    private final JdbcTemplate jdbc;

    ResponsibleTeacherAdapter(ResponsibleTeacherJpaRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override @Transactional
    public TeacherRecord assign(UUID activityProjectId, UUID teacherMembershipId, UUID userId) {
        var e = new ResponsibleTeacherEntity();
        e.setId(UUID.randomUUID());
        e.setActivityProjectId(activityProjectId);
        e.setTeacherMembershipId(teacherMembershipId);
        e.setCreatedAt(Instant.now());
        jpa.saveAndFlush(e);
        var rows = jdbc.query(JOIN_SQL + " WHERE rt.id = ?",
                (rs, rowNum) -> mapRecord(rs), e.getId());
        if (rows.isEmpty()) throw new IllegalStateException("Failed to load assigned teacher record");
        return rows.getFirst();
    }

    @Override @Transactional(readOnly = true)
    public List<TeacherRecord> findByActivityProject(UUID activityProjectId) {
        return jdbc.query(JOIN_SQL + " WHERE rt.activity_project_id = ? ORDER BY u.username",
                (rs, rowNum) -> mapRecord(rs),
                activityProjectId);
    }

    @Override @Transactional(readOnly = true)
    public Optional<TeacherRecord> findByActivityProjectAndUserId(UUID activityProjectId, UUID userId) {
        var rows = jdbc.query(JOIN_SQL + " WHERE rt.activity_project_id = ? AND u.id = ?",
                (rs, rowNum) -> mapRecord(rs),
                activityProjectId, userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override @Transactional
    public void unassignById(UUID assignmentId) {
        jpa.deleteById(assignmentId);
    }

    @Override @Transactional
    public void deleteAllByActivityProject(UUID activityProjectId) {
        jdbc.update("DELETE FROM responsible_teachers WHERE activity_project_id = ?", activityProjectId);
    }

    @Override @Transactional(readOnly = true)
    public boolean exists(UUID activityProjectId, UUID teacherMembershipId) {
        return jpa.existsByActivityProjectIdAndTeacherMembershipId(activityProjectId, teacherMembershipId);
    }

    @Override @Transactional(readOnly = true)
    public Map<UUID, Long> countAssignableByActivityProjects(List<UUID> activityProjectIds) {
        if (activityProjectIds.isEmpty()) return Map.of();
        var placeholders = String.join(",", Collections.nCopies(activityProjectIds.size(), "?"));
        var args = new ArrayList<>(activityProjectIds);
        var rows = jdbc.queryForList(
                "SELECT rt.activity_project_id, COUNT(*) AS cnt FROM responsible_teachers rt "
                        + "JOIN school_memberships sm ON rt.teacher_membership_id = sm.id "
                        + "JOIN users u ON sm.user_id = u.id "
                        + "WHERE rt.activity_project_id IN (" + placeholders + ") "
                        + "AND sm.status = 'ACTIVE' AND sm.role_in_school = 'TEACHER' AND u.account_status = 'NORMAL' "
                        + "GROUP BY rt.activity_project_id",
                args.toArray());
        Map<UUID, Long> result = new HashMap<>();
        for (var row : rows) {
            result.put((UUID) row.get("activity_project_id"), ((Number) row.get("cnt")).longValue());
        }
        return result;
    }

    private static final String JOIN_SQL =
            "SELECT rt.id, rt.activity_project_id, rt.teacher_membership_id, u.id AS user_id, "
                    + "u.username, COALESCE(tp.subject,'') AS subject, COALESCE(tp.title,'') AS title, "
                    + "sm.status AS membership_status, u.account_status "
                    + "FROM responsible_teachers rt "
                    + "JOIN school_memberships sm ON rt.teacher_membership_id = sm.id "
                    + "JOIN users u ON sm.user_id = u.id "
                    + "LEFT JOIN teacher_profiles tp ON tp.membership_id = sm.id";

    private TeacherRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TeacherRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("activity_project_id", UUID.class),
                rs.getObject("teacher_membership_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("subject"),
                rs.getString("title"),
                rs.getString("membership_status"),
                rs.getString("account_status"));
    }
}
