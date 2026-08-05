package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.StudentIdentityApplicationRepository;
import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class StudentIdentityApplicationRepositoryAdapter implements StudentIdentityApplicationRepository {

    private final StudentIdentityApplicationJpaRepository jpaRepository;

    StudentIdentityApplicationRepositoryAdapter(StudentIdentityApplicationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(StudentIdentityApplication application) {
        jpaRepository.save(StudentIdentityApplicationPersistenceMapper.toEntity(application));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentIdentityApplication> findById(StudentIdentityApplicationId id) {
        return jpaRepository.findById(id.value())
                .map(StudentIdentityApplicationPersistenceMapper::toDomain);
    }
}
