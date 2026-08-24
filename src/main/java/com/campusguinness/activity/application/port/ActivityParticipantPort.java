package com.campusguinness.activity.application.port;

import com.campusguinness.activity.application.query.model.ActivityParticipantResult;
import com.campusguinness.activity.application.query.model.ActivityDetailResult;
import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.internal.domain.ActivityParticipant;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityParticipantPort {
    Optional<UUID> findActivitySchool(UUID activityId);

    Optional<UUID> findActiveStudentMembership(UUID studentId, UUID schoolId);

    List<ActivityParticipantResult> findParticipants(UUID activityId, UUID schoolId);

    List<ActivityParticipantResult> findCandidates(UUID activityId, UUID schoolId, String query);

    boolean exists(UUID activityId, UUID studentMembershipId);

    void save(ActivityParticipant participant);

    boolean delete(UUID activityId, UUID studentMembershipId);

    QueryPage<ActivityListResult> findAssignedActivities(
            UUID studentId, UUID schoolId, int page, int size);

    Optional<ActivityDetailResult> findAssignedActivity(
            UUID studentId, UUID schoolId, UUID activityId);
}
