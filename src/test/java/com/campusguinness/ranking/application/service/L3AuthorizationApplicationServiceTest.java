package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.port.L3AuthorizationRepository;
import com.campusguinness.ranking.application.port.L3AuthorizationValidationPort;
import com.campusguinness.ranking.internal.domain.L3Authorization;
import com.campusguinness.ranking.internal.domain.L3AuthorizationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class L3AuthorizationApplicationServiceTest {
    @Mock L3AuthorizationRepository repo;
    @Mock CurrentActor currentActor;
    @Mock SchoolResourceAuthorization schoolAuthorization;
    @Mock PlatformGovernanceAuthorization platformAuthorization;
    @Mock L3AuthorizationValidationPort validation;
    L3AuthorizationApplicationService svc;
    UUID actorUserId;
    UUID schoolId;

    @BeforeEach void setUp() {
        actorUserId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        lenient().when(currentActor.requireUserId()).thenReturn(actorUserId);
        lenient().when(schoolAuthorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        lenient().when(schoolAuthorization.requireSchoolAdmin(any())).thenReturn(actorUserId);
        lenient().when(platformAuthorization.requireSuperAdmin()).thenReturn(actorUserId);
        svc = new L3AuthorizationApplicationService(repo, currentActor, schoolAuthorization, platformAuthorization, validation);
    }

    @Test void createDerivesSchoolScopeAndSavesDraft() {
        var r = svc.create(UUID.randomUUID(), UUID.randomUUID(), null, true, false);
        assertThat(r.status()).isEqualTo("DRAFT");
        var captor = forClass(L3Authorization.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().schoolId()).isEqualTo(schoolId);
        verify(schoolAuthorization).requireUniqueSchoolAdminSchool();
    }

    @Test void submitOwnDraft() {
        var a = draft();
        when(repo.findByIdForUpdate(any())).thenReturn(Optional.of(a));
        assertThat(svc.submit(a.id().value()).status()).isEqualTo("PENDING_REVIEW");
        verify(schoolAuthorization).requireSchoolAdmin(a.schoolId());
        verify(repo).save(any());
    }

    @Test void approveAsSuperAdmin() {
        var a = submitted();
        when(repo.findByIdForUpdate(any())).thenReturn(Optional.of(a));
        assertThat(svc.approve(a.id().value(), "ok").status()).isEqualTo("APPROVED");
        var captor = forClass(L3Authorization.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().reviewedBy()).isEqualTo(actorUserId);
        verify(platformAuthorization).requireSuperAdmin();
    }

    @Test void rejectAsSuperAdminRequiresReason() {
        var a = submitted();
        when(repo.findByIdForUpdate(any())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> svc.reject(a.id().value(), " "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test void withdrawOwnDraft() {
        var a = draft();
        when(repo.findByIdForUpdate(any())).thenReturn(Optional.of(a));
        assertThat(svc.withdraw(a.id().value(), "duplicate").status()).isEqualTo("WITHDRAWN");
        verify(schoolAuthorization).requireSchoolAdmin(a.schoolId());
        verify(repo).save(any());
    }

    @Test void notFound() {
        when(repo.findByIdForUpdate(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.approve(UUID.randomUUID(), "ok"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private L3Authorization submitted() {
        var a = draft();
        a.submit();
        return a;
    }

    private L3Authorization draft() {
        return L3Authorization.create(new L3Authorization.Builder()
                .id(new L3AuthorizationId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .ruleVersionId(UUID.randomUUID())
                .dataScope("{}")
                .allowSchoolName(true));
    }
}
