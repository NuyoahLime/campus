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
    ActivityResultRepositoryAdapter(ActivityResultJpaRepository r) { this.jpaRepository = r; }
    @Override @Transactional public void save(ActivityResult a) { jpaRepository.save(ActivityResultPersistenceMapper.toEntity(a)); }
    @Override @Transactional(readOnly = true) public Optional<ActivityResult> findById(ActivityResultId id) {
        return jpaRepository.findById(id.value()).map(ActivityResultPersistenceMapper::toDomain);
    }
}
