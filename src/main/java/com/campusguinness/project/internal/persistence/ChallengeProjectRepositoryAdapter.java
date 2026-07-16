package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.internal.domain.ChallengeProject;
import com.campusguinness.project.internal.domain.ChallengeProjectId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Infrastructure adapter: implements domain port using JPA. */
@Component
public class ChallengeProjectRepositoryAdapter implements ChallengeProjectRepository {

    private final ChallengeProjectJpaRepository jpaRepository;

    public ChallengeProjectRepositoryAdapter(ChallengeProjectJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(ChallengeProject project) {
        jpaRepository.save(ChallengeProjectPersistenceMapper.toEntity(project));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChallengeProject> findById(ChallengeProjectId id) {
        return jpaRepository.findById(id.value())
                .map(ChallengeProjectPersistenceMapper::toDomain);
    }
}
