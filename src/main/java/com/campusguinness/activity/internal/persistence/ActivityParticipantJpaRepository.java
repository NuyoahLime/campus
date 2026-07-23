package com.campusguinness.activity.internal.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityParticipantJpaRepository extends JpaRepository<ActivityParticipantEntity, UUID> {
    Page<ActivityParticipantEntity> findByActivityId(UUID activityId, Pageable pageable);
    List<ActivityParticipantEntity> findByActivityId(UUID activityId);
    Optional<ActivityParticipantEntity> findByActivityIdAndStudentMembershipId(UUID activityId, UUID studentMembershipId);
    boolean existsByActivityIdAndStudentMembershipId(UUID activityId, UUID studentMembershipId);
    List<ActivityParticipantEntity> findByStudentMembershipId(UUID studentMembershipId);
    List<ActivityParticipantEntity> findByStudentMembershipIdIn(List<UUID> membershipIds);
}
