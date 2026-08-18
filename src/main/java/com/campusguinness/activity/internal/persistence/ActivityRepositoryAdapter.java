package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class ActivityRepositoryAdapter implements ActivityRepository {
    private final ActivityJpaRepository jpaRepository;
    ActivityRepositoryAdapter(ActivityJpaRepository r) { this.jpaRepository = r; }

    @Override @Transactional
    public void save(Activity a) {
        var entity = jpaRepository.findById(a.id().value())
                .orElseGet(() -> ActivityPersistenceMapper.toEntity(a));
        ActivityPersistenceMapper.updateEntity(a, entity);
        jpaRepository.save(entity);
    }

    @Override @Transactional(readOnly = true)
    public Optional<Activity> findById(ActivityId id) {
        return jpaRepository.findById(id.value()).map(ActivityPersistenceMapper::toDomain);
    }
}
