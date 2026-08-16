package com.campusguinness.school.application.query;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.port.SchoolAdminGovernanceQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolAdminGovernanceQueryServiceTest {

    @Mock SchoolAdminGovernanceQueryPort queryPort;
    @Mock PlatformGovernanceAuthorization authorization;

    private SchoolAdminGovernanceQueryService service;

    @BeforeEach
    void setUp() {
        service = new SchoolAdminGovernanceQueryService(queryPort, authorization);
    }

    @Test
    void normalizesFiltersAfterAuthoritativeAuthorization() {
        when(queryPort.findSchools("PENDING_ENABLE", "Campus", 1, 20))
                .thenReturn(new QueryPage<>(List.of(), 1, 20, 0));

        service.listSchools(1, 20, " pending_enable ", " Campus ");

        verify(authorization).requireSuperAdmin();
        verify(queryPort).findSchools("PENDING_ENABLE", "Campus", 1, 20);
    }

    @Test
    void rejectsInvalidPaginationStatusAndSearch() {
        assertThatThrownBy(() -> service.listSchools(-1, 20, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be >= 0");
        assertThatThrownBy(() -> service.listSchools(0, 101, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> service.listSchools(0, 20, "UNKNOWN", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("school status is invalid");
        assertThatThrownBy(() -> service.listSchools(0, 20, null, "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("q max 200 chars");
    }

    @Test
    void unknownSchoolUsesDedicatedErrorCode() {
        UUID schoolId = UUID.randomUUID();
        when(queryPort.findSchool(schoolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.schoolDetail(schoolId))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("SCHOOL_NOT_FOUND");
    }

    @Test
    void invitationDetailIsScopedAndUsesAntiEnumerationError() {
        UUID schoolId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        when(queryPort.findInvitation(schoolId, invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invitationDetail(schoolId, invitationId))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting(ex -> ((IdentityApplicationException) ex).code())
                .isEqualTo("INVITATION_NOT_FOUND");

        verify(queryPort).findInvitation(schoolId, invitationId);
    }

    @Test
    void deniedGovernanceIdentityStopsBeforePersistence() {
        when(authorization.requireSuperAdmin()).thenThrow(new IdentityApplicationException(
                "PLATFORM_GOVERNANCE_DENIED", "Platform governance access denied."
        ));

        assertThatThrownBy(() -> service.listSchools(0, 20, null, null))
                .isInstanceOf(IdentityApplicationException.class);
        verifyNoInteractions(queryPort);
    }
}
