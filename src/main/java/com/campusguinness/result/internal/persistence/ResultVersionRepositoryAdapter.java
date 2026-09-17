package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.application.port.ResultVersionRepository;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
class ResultVersionRepositoryAdapter implements ResultVersionRepository {
    private final EntityManager entityManager;
    private final ResultVersionJpaRepository jpaRepository;

    ResultVersionRepositoryAdapter(EntityManager entityManager, ResultVersionJpaRepository jpaRepository) {
        this.entityManager = entityManager;
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void create(ResultVersion resultVersion) {
        entityManager.persist(ResultVersionPersistenceMapper.toEntity(resultVersion));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResultVersion> findById(ResultVersionId id) {
        return jpaRepository.findById(id.value()).map(ResultVersionPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public int nextVersionNumberFor(ActivityResultId resultId) {
        Objects.requireNonNull(resultId, "resultId required");
        return jpaRepository.maxVersionNumber(resultId.value()) + 1;
    }

    @Override
    @Transactional
    public void markPublishedInternally(ResultVersionId id, Instant publishedAt) {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(publishedAt, "publishedAt required");
        int updated = jpaRepository.markPublishedInternallyIfUnpublished(id.value(), publishedAt);
        if (updated != 1) {
            throw new IllegalStateException(
                    "ResultVersion does not exist or is already published internally: " + id.value());
        }
    }
}
