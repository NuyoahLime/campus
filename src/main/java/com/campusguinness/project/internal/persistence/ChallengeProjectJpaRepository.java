package com.campusguinness.project.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ChallengeProjectJpaRepository extends JpaRepository<ChallengeProjectEntity, UUID>,
        JpaSpecificationExecutor<ChallengeProjectEntity> {
    org.springframework.data.domain.Page<ChallengeProjectEntity> findByProjectStatus(String status,
            org.springframework.data.domain.Pageable pageable);
}
