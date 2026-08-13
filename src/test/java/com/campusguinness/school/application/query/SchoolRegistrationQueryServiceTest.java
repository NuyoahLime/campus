package com.campusguinness.school.application.query;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.exception.SchoolRegistrationNotFoundException;
import com.campusguinness.school.application.query.port.SchoolRegistrationQueryPort;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolRegistrationQueryServiceTest {

    @Mock SchoolRegistrationQueryPort queryPort;
    @Mock PlatformGovernanceAuthorization authorization;

    private SchoolRegistrationQueryService service;

    @BeforeEach
    void setUp() {
        service = new SchoolRegistrationQueryService(queryPort, authorization);
    }

    @Test
    void normalizesStatusAndDelegatesAfterAuthoritativeAuthorization() {
        when(queryPort.findAll("SUBMITTED", 1, 20))
                .thenReturn(new QueryPage<>(List.of(), 1, 20, 0));

        service.list(1, 20, " submitted ");

        verify(authorization).requireSuperAdmin();
        verify(queryPort).findAll("SUBMITTED", 1, 20);
    }

    @Test
    void absentStatusQueriesAllRegistrations() {
        when(queryPort.findAll(null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(), 0, 20, 0));

        service.list(0, 20, null);

        verify(queryPort).findAll(null, 0, 20);
    }

    @Test
    void rejectsInvalidPaginationAndUnknownStatus() {
        assertThatThrownBy(() -> service.list(-1, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be >= 0");
        assertThatThrownBy(() -> service.list(0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> service.list(0, 101, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> service.list(0, 20, "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status must be one of");
    }

    @Test
    void missingDetailUsesDedicatedNotFoundException() {
        UUID registrationId = UUID.randomUUID();
        when(queryPort.findById(registrationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(registrationId))
                .isInstanceOf(SchoolRegistrationNotFoundException.class)
                .hasMessageContaining(registrationId.toString());

        verify(authorization).requireSuperAdmin();
    }
}
