package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.internal.domain.ActivityApplication;
import com.campusguinness.activity.internal.domain.ActivityApplicationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Override @Transactional(readOnly = true)
    public List<ActivityApplication> findByApplicantId(UUID applicantId) {
        return jpaRepository.findByApplicantId(applicantId).stream()
                .map(ActivityApplicationPersistenceMapper::toDomain).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<ActivityApplication> findByIdAndApplicantId(UUID id, UUID applicantId) {
        return jpaRepository.findByIdAndApplicantId(id, applicantId)
                .map(ActivityApplicationPersistenceMapper::toDomain);
    }
}
