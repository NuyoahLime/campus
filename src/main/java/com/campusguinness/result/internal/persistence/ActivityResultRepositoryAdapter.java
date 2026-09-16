package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.internal.domain.ActivityResult;
import com.campusguinness.result.internal.domain.ActivityResultId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class ActivityResultRepositoryAdapter implements ActivityResultRepository {
    private final ActivityResultJpaRepository jpaRepository;
    private final ResultVersionJpaRepository resultVersionJpaRepository;

    ActivityResultRepositoryAdapter(
            ActivityResultJpaRepository r,
            ResultVersionJpaRepository resultVersionJpaRepository) {
        this.jpaRepository = r;
        this.resultVersionJpaRepository = resultVersionJpaRepository;
    }

    @Override
    @Transactional
    public void save(ActivityResult a) {
        requireOwnedVersion(a.currentCandidateVersionId(), a, "currentCandidateVersionId");
        requireOwnedVersion(a.currentInternalVersionId(), a, "currentInternalVersionId");
        requireOwnedVersion(a.currentPublicVersionId(), a, "currentPublicVersionId");
        jpaRepository.save(ActivityResultPersistenceMapper.toEntity(a));
    }

    private void requireOwnedVersion(java.util.UUID versionId, ActivityResult result, String pointerName) {
        if (versionId != null
                && !resultVersionJpaRepository.existsByIdAndResultId(versionId, result.id().value())) {
            throw new IllegalArgumentException(pointerName + " must reference a version of the same ActivityResult");
        }
    }
    @Override @Transactional(readOnly = true) public Optional<ActivityResult> findById(ActivityResultId id) {
        return jpaRepository.findById(id.value()).map(ActivityResultPersistenceMapper::toDomain);
    }
}
