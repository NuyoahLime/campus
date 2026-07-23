package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ActivityParticipantPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ActivityParticipantAdapter implements ActivityParticipantPort {
    private final ActivityParticipantJpaRepository jpa;
    ActivityParticipantAdapter(ActivityParticipantJpaRepository jpa) { this.jpa = jpa; }

    @Override @Transactional
    public ParticipantRecord add(UUID activityId, UUID studentMembershipId) {
        var e = new ActivityParticipantEntity();
        e.setId(UUID.randomUUID());
        e.setActivityId(activityId);
        e.setStudentMembershipId(studentMembershipId);
        e.setCreatedAt(Instant.now());
        jpa.save(e);
        return toRecord(e);
    }

    @Override @Transactional(readOnly = true)
    public List<ParticipantRecord> findByActivity(UUID activityId) {
        return jpa.findByActivityId(activityId).stream().map(this::toRecord).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<ParticipantRecord> findByActivityAndMembership(UUID activityId, UUID studentMembershipId) {
        return jpa.findByActivityIdAndStudentMembershipId(activityId, studentMembershipId).map(this::toRecord);
    }

    @Override @Transactional
    public void remove(UUID participantId) {
        jpa.deleteById(participantId);
    }

    @Override @Transactional(readOnly = true)
    public boolean existsByActivityAndMembership(UUID activityId, UUID studentMembershipId) {
        return jpa.existsByActivityIdAndStudentMembershipId(activityId, studentMembershipId);
    }

    @Override @Transactional(readOnly = true)
    public Optional<ParticipantRecord> findById(UUID id) {
        return jpa.findById(id).map(this::toRecord);
    }

    @Override @Transactional(readOnly = true)
    public List<ParticipantRecord> findByMembershipIds(List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) return List.of();
        return jpa.findByStudentMembershipIdIn(membershipIds).stream().map(this::toRecord).toList();
    }

    private ParticipantRecord toRecord(ActivityParticipantEntity e) {
        return new ParticipantRecord(e.getId(), e.getActivityId(), e.getStudentMembershipId(), e.getCreatedAt());
    }
}
