package com.campusguinness.activity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityProjectJpaRepository extends JpaRepository<ActivityProjectEntity, UUID> {
    List<ActivityProjectEntity> findByActivityId(UUID activityId);
    Optional<ActivityProjectEntity> findByActivityIdAndProjectId(UUID activityId, UUID projectId);
    boolean existsByActivityIdAndProjectId(UUID activityId, UUID projectId);
}
