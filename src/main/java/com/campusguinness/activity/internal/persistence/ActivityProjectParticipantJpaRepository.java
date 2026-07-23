package com.campusguinness.activity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityProjectParticipantJpaRepository extends JpaRepository<ActivityProjectParticipantEntity, UUID> {
    List<ActivityProjectParticipantEntity> findByActivityProjectId(UUID projectId);
    Optional<ActivityProjectParticipantEntity> findByActivityProjectIdAndActivityParticipantId(UUID projectId, UUID participantId);
    boolean existsByActivityProjectIdAndActivityParticipantId(UUID projectId, UUID participantId);
    long deleteByActivityProjectIdAndActivityParticipantId(UUID projectId, UUID participantId);
    List<ActivityProjectParticipantEntity> findByActivityParticipantId(UUID participantId);
    List<ActivityProjectParticipantEntity> findByActivityParticipantIdIn(List<UUID> participantIds);
}
