package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
class StudentIdentityApplicationRepositoryAdapter implements StudentIdentityApplicationRepository {

    private final StudentIdentityApplicationJpaRepository jpaRepository;

    StudentIdentityApplicationRepositoryAdapter(StudentIdentityApplicationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(StudentIdentityApplication application) {
        var existing = jpaRepository.findById(application.id().value());
        if (existing.isPresent()) {
            StudentIdentityApplicationPersistenceMapper.updateEntity(existing.get(), application);
            jpaRepository.save(existing.get());
        } else {
            jpaRepository.save(StudentIdentityApplicationPersistenceMapper.toEntity(application));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentIdentityApplication> findById(StudentIdentityApplicationId id) {
        return jpaRepository.findById(id.value())
                .map(StudentIdentityApplicationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<StudentIdentityApplication> findByIdForUpdate(StudentIdentityApplicationId id) {
        return jpaRepository.findByIdForUpdate(id.value())
                .map(StudentIdentityApplicationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<StudentIdentityApplication> findLatestByUserIdForUpdate(UUID userId) {
        return jpaRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(StudentIdentityApplicationPersistenceMapper::toDomain);
    }
}
