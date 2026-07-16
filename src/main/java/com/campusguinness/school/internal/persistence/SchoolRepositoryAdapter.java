package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.application.port.SchoolRepository;
import com.campusguinness.school.internal.domain.School;
import com.campusguinness.school.internal.domain.SchoolId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class SchoolRepositoryAdapter implements SchoolRepository {

    private final SchoolJpaRepository jpaRepository;

    SchoolRepositoryAdapter(SchoolJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(School school) {
        jpaRepository.save(SchoolPersistenceMapper.toEntity(school));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<School> findById(SchoolId id) {
        return jpaRepository.findById(id.value())
                .map(SchoolPersistenceMapper::toDomain);
    }
}
