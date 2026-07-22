package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        jpa.save(e);
        return new TeacherRecord(e.getId(), activityProjectId, teacherMembershipId, userId);
    }

    @Override @Transactional(readOnly = true)
    public List<TeacherRecord> findByActivityProject(UUID activityProjectId) {
        return jpa.findByActivityProjectId(activityProjectId).stream()
                .map(e -> {
                    UUID userId = resolveUserId(e.getTeacherMembershipId());
                    return new TeacherRecord(e.getId(), e.getActivityProjectId(),
                            e.getTeacherMembershipId(), userId);
                }).toList();
    }

    @Override @Transactional
    public void unassign(UUID activityProjectId, UUID teacherMembershipId) {
        jpa.deleteByActivityProjectIdAndTeacherMembershipId(activityProjectId, teacherMembershipId);
    }

    @Override @Transactional(readOnly = true)
    public boolean exists(UUID activityProjectId, UUID teacherMembershipId) {
        return jpa.existsByActivityProjectIdAndTeacherMembershipId(activityProjectId, teacherMembershipId);
    }

    private UUID resolveUserId(UUID membershipId) {
        var rows = jdbc.queryForList(
                "SELECT user_id FROM school_memberships WHERE id = ?", UUID.class, membershipId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
