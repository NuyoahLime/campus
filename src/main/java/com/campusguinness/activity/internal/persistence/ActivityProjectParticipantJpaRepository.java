package com.campusguinness.activity.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ActivityProjectParticipantJpaRepository extends JpaRepository<ActivityProjectParticipantEntity, UUID> {
    List<ActivityProjectParticipantEntity> findByActivityProjectId(UUID projectId);
    Optional<ActivityProjectParticipantEntity> findByActivityProjectIdAndActivityApplicationId(UUID projectId, UUID appId);
    boolean existsByActivityProjectIdAndActivityApplicationId(UUID projectId, UUID appId);
    void deleteByActivityProjectIdAndActivityApplicationId(UUID projectId, UUID appId);
}
