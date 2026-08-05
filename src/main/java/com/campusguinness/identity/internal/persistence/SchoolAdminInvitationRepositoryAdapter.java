package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.SchoolAdminInvitationRepository;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class SchoolAdminInvitationRepositoryAdapter implements SchoolAdminInvitationRepository {

    private final SchoolAdminInvitationJpaRepository jpaRepository;

    SchoolAdminInvitationRepositoryAdapter(SchoolAdminInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(SchoolAdminInvitation invitation) {
        jpaRepository.save(SchoolAdminInvitationPersistenceMapper.toEntity(invitation));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolAdminInvitation> findById(SchoolAdminInvitationId id) {
        return jpaRepository.findById(id.value())
                .map(SchoolAdminInvitationPersistenceMapper::toDomain);
    }
}
