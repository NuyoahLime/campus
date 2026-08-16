package com.campusguinness.school.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolAdminAccountResult;
import com.campusguinness.school.application.query.model.SchoolAdminInvitationQueryResult;
import com.campusguinness.school.application.query.model.SchoolGovernanceDetailResult;
import com.campusguinness.school.application.query.model.SchoolGovernanceListResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolAdminGovernanceQueryPort {

    QueryPage<SchoolGovernanceListResult> findSchools(String status, String search, int page, int size);

    Optional<SchoolGovernanceDetailResult> findSchool(UUID schoolId);

    List<SchoolAdminAccountResult> findSchoolAdmins(UUID schoolId);

    QueryPage<SchoolAdminInvitationQueryResult> findInvitations(
            UUID schoolId,
            String status,
            int page,
            int size
    );

    Optional<SchoolAdminInvitationQueryResult> findInvitation(UUID schoolId, UUID invitationId);
}
