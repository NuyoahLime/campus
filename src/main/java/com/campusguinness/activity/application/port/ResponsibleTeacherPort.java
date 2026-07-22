package com.campusguinness.activity.application.port;

import java.util.List;
import java.util.UUID;

public interface ResponsibleTeacherPort {
    record TeacherRecord(UUID id, UUID activityProjectId, UUID teacherMembershipId, UUID userId) {}

    TeacherRecord assign(UUID activityProjectId, UUID teacherMembershipId, UUID userId);
    List<TeacherRecord> findByActivityProject(UUID activityProjectId);
    void unassign(UUID activityProjectId, UUID teacherMembershipId);
    boolean exists(UUID activityProjectId, UUID teacherMembershipId);
}
