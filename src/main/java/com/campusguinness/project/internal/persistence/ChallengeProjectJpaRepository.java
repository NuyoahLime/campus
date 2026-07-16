package com.campusguinness.project.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ChallengeProjectJpaRepository extends JpaRepository<ChallengeProjectEntity, UUID> {
    org.springframework.data.domain.Page<ChallengeProjectEntity> findByProjectStatus(String status, org.springframework.data.domain.Pageable pageable);
}
