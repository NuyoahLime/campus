package com.campusguinness.activity.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ResponsibleTeacherPort {

    record TeacherRecord(UUID id, UUID activityProjectId, UUID teacherMembershipId, UUID userId,
                         String username, String subject, String title, String membershipStatus,
                         String accountStatus) {}

    TeacherRecord assign(UUID activityProjectId, UUID teacherMembershipId, UUID userId);
    List<TeacherRecord> findByActivityProject(UUID activityProjectId);
    Optional<TeacherRecord> findByActivityProjectAndUserId(UUID activityProjectId, UUID userId);
    void unassignById(UUID assignmentId);
    boolean exists(UUID activityProjectId, UUID teacherMembershipId);
    void deleteAllByActivityProject(UUID activityProjectId);

    /** Bulk count of assignable teachers per activity project. */
    Map<UUID, Long> countAssignableByActivityProjects(List<UUID> activityProjectIds);
}
