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
        var existing = jpaRepository.findById(registration.id().value());
        if (existing.isPresent()) {
            if (existing.get().getVersion() != registration.version()) {
                throw new SchoolRegistrationConcurrentReviewException();
            }
            SchoolRegistrationPersistenceMapper.updateEntity(existing.get(), registration);
            jpaRepository.save(existing.get());
        } else {
            jpaRepository.save(SchoolRegistrationPersistenceMapper.toEntity(registration));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolRegistration> findById(SchoolRegistrationId id) {
        return jpaRepository.findById(id.value())
                .map(SchoolRegistrationPersistenceMapper::toDomain);
    }
}
