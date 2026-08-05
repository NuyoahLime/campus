package com.campusguinness.identity.application.port;

import com.campusguinness.identity.internal.domain.SchoolAdminInvitation;
import com.campusguinness.identity.internal.domain.SchoolAdminInvitationId;

import java.util.Optional;

public interface SchoolAdminInvitationRepository {
    void save(SchoolAdminInvitation invitation);
    Optional<SchoolAdminInvitation> findById(SchoolAdminInvitationId id);
}
