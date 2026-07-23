package com.campusguinness.activity.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityParticipantPort {
    record ParticipantRecord(UUID id, UUID activityId, UUID studentMembershipId, java.time.Instant createdAt) {}
    record ParticipantView(UUID participantId, UUID activityId, UUID studentId, UUID membershipId) {}

    ParticipantRecord add(UUID activityId, UUID studentMembershipId);
    List<ParticipantRecord> findByActivity(UUID activityId);
    Optional<ParticipantRecord> findByActivityAndMembership(UUID activityId, UUID studentMembershipId);
    void remove(UUID participantId);
    boolean existsByActivityAndMembership(UUID activityId, UUID studentMembershipId);
    Optional<ParticipantRecord> findById(UUID id);
    List<ParticipantRecord> findByMembershipIds(List<UUID> membershipIds);
}
