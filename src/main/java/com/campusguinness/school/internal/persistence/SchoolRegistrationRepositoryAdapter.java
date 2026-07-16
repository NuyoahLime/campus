package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.application.port.SchoolRegistrationRepository;
import com.campusguinness.school.internal.domain.SchoolRegistration;
import com.campusguinness.school.internal.domain.SchoolRegistrationId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class SchoolRegistrationRepositoryAdapter implements SchoolRegistrationRepository {

    private final SchoolRegistrationJpaRepository jpaRepository;

    SchoolRegistrationRepositoryAdapter(SchoolRegistrationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(SchoolRegistration registration) {
        jpaRepository.save(SchoolRegistrationPersistenceMapper.toEntity(registration));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolRegistration> findById(SchoolRegistrationId id) {
        return jpaRepository.findById(id.value())
                .map(SchoolRegistrationPersistenceMapper::toDomain);
    }
}
