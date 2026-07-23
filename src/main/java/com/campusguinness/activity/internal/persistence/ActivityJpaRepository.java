package com.campusguinness.activity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ActivityJpaRepository extends JpaRepository<ActivityEntity, UUID>,
        JpaSpecificationExecutor<ActivityEntity> {
    org.springframework.data.domain.Page<ActivityEntity> findByExecutionStatusIn(List<String> statuses,
            org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ActivityEntity> findByExecutionStatusInAndPublicStatus(
            List<String> executionStatuses, String publicStatus,
            org.springframework.data.domain.Pageable pageable);
}
