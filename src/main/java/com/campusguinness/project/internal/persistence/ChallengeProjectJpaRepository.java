package com.campusguinness.project.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import java.util.Optional;

public interface ChallengeProjectJpaRepository extends JpaRepository<ChallengeProjectEntity, UUID>,
        JpaSpecificationExecutor<ChallengeProjectEntity> {
    Page<ChallengeProjectEntity> findByProjectStatus(String status, Pageable pageable);

    Optional<ChallengeProjectEntity> findByIdAndProjectStatus(UUID id, String status);
}
