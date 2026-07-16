package com.campusguinness.activity.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ActivityApplicationJpaRepository extends JpaRepository<ActivityApplicationEntity, UUID> {
    Optional<ActivityApplicationEntity> findByCreatedActivityId(UUID activityId);
}
