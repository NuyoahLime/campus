package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.query.model.ParticipantListResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityParticipantQueryPort {
    QueryPage<ParticipantListResult> findByActivity(UUID activityId, String keyword, int page, int size);

    /** Returns the participant IDs for a given student membership across activities. */
    List<UUID> findParticipantIdsByMembership(UUID studentMembershipId);

    /** Returns participant IDs for a student across multiple memberships. */
    List<UUID> findParticipantIdsByMembershipIds(List<UUID> membershipIds);

    /** Returns the participant record for a specific activity and student memberships. */
    Optional<ParticipantListResult> findByActivityAndMemberships(UUID activityId, List<UUID> membershipIds);
}
