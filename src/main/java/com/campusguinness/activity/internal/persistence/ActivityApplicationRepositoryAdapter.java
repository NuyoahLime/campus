package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.internal.domain.ActivityApplication;
import com.campusguinness.activity.internal.domain.ActivityApplicationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Component
class ActivityApplicationRepositoryAdapter implements ActivityApplicationRepository {
    private final ActivityApplicationJpaRepository jpaRepository;
    ActivityApplicationRepositoryAdapter(ActivityApplicationJpaRepository r) { this.jpaRepository = r; }

    @Override @Transactional
    public void save(ActivityApplication a) { jpaRepository.save(ActivityApplicationPersistenceMapper.toEntity(a)); }

    @Override @Transactional(readOnly = true)
    public Optional<ActivityApplication> findById(ActivityApplicationId id) {
        return jpaRepository.findById(id.value()).map(ActivityApplicationPersistenceMapper::toDomain);
    }
}
