package com.campusguinness.identity.application.port;

import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;

import java.util.Optional;
import java.util.UUID;

public interface SchoolAdminInvitationRepository {
    void save(SchoolAdminInvitation invitation);
    void saveAndFlush(SchoolAdminInvitation invitation);
    Optional<SchoolAdminInvitation> findById(SchoolAdminInvitationId id);
    Optional<SchoolAdminInvitation> findByIdForUpdate(SchoolAdminInvitationId id);
    Optional<SchoolAdminInvitation> findPendingByUserIdForUpdate(UUID userId);
}
