package com.campusguinness.activity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityApplicationJpaRepository extends JpaRepository<ActivityApplicationEntity, UUID>,
        JpaSpecificationExecutor<ActivityApplicationEntity> {
    Optional<ActivityApplicationEntity> findByCreatedActivityId(UUID activityId);
    List<ActivityApplicationEntity> findByApplicantId(UUID applicantId);
    Optional<ActivityApplicationEntity> findByIdAndApplicantId(UUID id, UUID applicantId);
}
