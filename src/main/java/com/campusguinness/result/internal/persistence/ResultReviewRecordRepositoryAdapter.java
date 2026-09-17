package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.application.port.ResultReviewRecordRepository;
import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewRecord;
import com.campusguinness.result.internal.domain.ResultVersionId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Component
class ResultReviewRecordRepositoryAdapter implements ResultReviewRecordRepository {
    private final EntityManager entityManager;

    ResultReviewRecordRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void append(ResultReviewRecord record) {
        Objects.requireNonNull(record, "record required");
        entityManager.persist(ResultReviewRecordPersistenceMapper.toEntity(record));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultReviewRecord> findByResult(ActivityResultId resultId) {
        Objects.requireNonNull(resultId, "resultId required");
        return entityManager.createQuery("""
                        SELECT record
                        FROM ResultReviewRecordEntity record
                        WHERE record.resultId = :resultId
                        ORDER BY record.createdAt ASC, record.id ASC
                        """, ResultReviewRecordEntity.class)
                .setParameter("resultId", resultId.value())
                .getResultList().stream()
                .map(ResultReviewRecordPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultReviewRecord> findByResultAndVersion(
            ActivityResultId resultId,
            ResultVersionId versionId) {
        Objects.requireNonNull(resultId, "resultId required");
        Objects.requireNonNull(versionId, "versionId required");
        return entityManager.createQuery("""
                        SELECT record
                        FROM ResultReviewRecordEntity record
                        WHERE record.resultId = :resultId
                          AND record.resultVersionId = :versionId
                        ORDER BY record.createdAt ASC, record.id ASC
                        """, ResultReviewRecordEntity.class)
                .setParameter("resultId", resultId.value())
                .setParameter("versionId", versionId.value())
                .getResultList().stream()
                .map(ResultReviewRecordPersistenceMapper::toDomain)
                .toList();
    }
}
