package com.campusguinness.result.internal.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ActivityResultJpaRepository extends JpaRepository<ActivityResultEntity, UUID> {
    Optional<ActivityResultEntity> findByActivityId(UUID activityId);
}
