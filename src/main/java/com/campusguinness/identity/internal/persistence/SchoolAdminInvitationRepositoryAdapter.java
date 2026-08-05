package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.SchoolAdminInvitationRepository;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
class SchoolAdminInvitationRepositoryAdapter implements SchoolAdminInvitationRepository {

    private final SchoolAdminInvitationJpaRepository jpaRepository;

    SchoolAdminInvitationRepositoryAdapter(SchoolAdminInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(SchoolAdminInvitation invitation) {
        saveInternal(invitation);
    }

    @Override
    @Transactional
    public void saveAndFlush(SchoolAdminInvitation invitation) {
        saveInternal(invitation);
        jpaRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolAdminInvitation> findById(SchoolAdminInvitationId id) {
        return jpaRepository.findById(id.value())
                .map(SchoolAdminInvitationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<SchoolAdminInvitation> findByIdForUpdate(SchoolAdminInvitationId id) {
        return jpaRepository.findByIdForUpdate(id.value())
                .map(SchoolAdminInvitationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<SchoolAdminInvitation> findPendingByUserIdForUpdate(UUID userId) {
        return jpaRepository.findPendingByUserIdForUpdate(userId)
                .map(SchoolAdminInvitationPersistenceMapper::toDomain);
    }

    private void saveInternal(SchoolAdminInvitation invitation) {
        var existing = jpaRepository.findById(invitation.id().value());
        if (existing.isPresent()) {
            SchoolAdminInvitationPersistenceMapper.updateEntity(existing.get(), invitation);
            jpaRepository.save(existing.get());
        } else {
            jpaRepository.save(SchoolAdminInvitationPersistenceMapper.toEntity(invitation));
        }
    }
}
