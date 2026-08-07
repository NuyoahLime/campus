package com.campusguinness.identity.application.query;

import java.util.Optional;
import java.util.UUID;

public interface LoginBusinessStateQuery {
    Optional<LatestStudentIdentityApplicationState> findLatestStudentApplication(UUID userId);
    Optional<SchoolAdminInvitationLoginState> findLatestSchoolAdminInvitation(UUID userId);
}
