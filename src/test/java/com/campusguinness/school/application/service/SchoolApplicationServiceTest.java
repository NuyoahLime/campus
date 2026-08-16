package com.campusguinness.school.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.school.application.port.SchoolRepository;
import com.campusguinness.school.application.query.model.SchoolGovernanceDetailResult;
import com.campusguinness.school.application.query.port.SchoolAdminGovernanceQueryPort;
import com.campusguinness.school.internal.domain.School;
import com.campusguinness.school.internal.domain.SchoolId;
import com.campusguinness.school.internal.domain.SchoolStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolApplicationServiceTest {

    @Mock SchoolRepository repository;
    @Mock SchoolAdminGovernanceQueryPort governanceQueries;
    @Mock PlatformGovernanceAuthorization authorization;
    @Mock AuditRecordCommandPort audit;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SchoolApplicationService service;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        when(authorization.requireSuperAdmin()).thenReturn(actorId);
        service = new SchoolApplicationService(
                repository,
                governanceQueries,
                authorization,
                audit,
                objectMapper
        );
    }

    @Test
    void createsSchoolInPendingEnable() {
        var result = service.create(
                "test", "USCC", "123", "INT-001", "PRIMARY", "Beijing",
                "addr", "name", "phone", "email"
        );

        assertThat(result.status()).isEqualTo("PENDING_ENABLE");
        verify(repository).save(any(School.class));
    }

    @Test
    void activateRequiresTwoNormalActiveSchoolAdmins() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.PENDING_ENABLE)));
        when(governanceQueries.findSchool(id)).thenReturn(Optional.of(detail(id, 1)));

        assertThatThrownBy(() -> service.activate(id, "administrators configured"))
                .isInstanceOfSatisfying(IdentityApplicationException.class, ex ->
                        assertThat(ex.code()).isEqualTo("SCHOOL_ADMIN_CONFIGURATION_INSUFFICIENT"));

        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void activatePersistsStatusAndAuthoritativeAudit() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.PENDING_ENABLE)));
        when(governanceQueries.findSchool(id)).thenReturn(Optional.of(detail(id, 2)));

        assertThat(service.activate(id, "  administrators configured  ").status()).isEqualTo("NORMAL");

        verify(repository).save(any(School.class));
        var auditCaptor = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(audit).record(auditCaptor.capture());
        var command = auditCaptor.getValue();
        assertThat(command.actorId()).isEqualTo(actorId);
        assertThat(command.schoolId()).isEqualTo(id);
        assertThat(command.targetId()).isEqualTo(id);
        assertThat(command.action()).isEqualTo("SCHOOL_ACTIVATE");
        var detail = objectMapper.readTree(command.detail());
        assertThat(detail.get("oldStatus").asText()).isEqualTo("PENDING_ENABLE");
        assertThat(detail.get("newStatus").asText()).isEqualTo("NORMAL");
        assertThat(detail.get("reason").asText()).isEqualTo("administrators configured");
    }

    @Test
    void suspendNormalSchool() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.NORMAL)));

        assertThat(service.suspend(id, "platform governance pause").status()).isEqualTo("SUSPENDED");

        verifyAuditAction("SCHOOL_SUSPEND");
    }

    @Test
    void restoreRequiresTwoNormalActiveSchoolAdmins() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.SUSPENDED)));
        when(governanceQueries.findSchool(id)).thenReturn(Optional.of(detail(id, 1)));

        assertThatThrownBy(() -> service.restore(id, "issue resolved"))
                .isInstanceOfSatisfying(IdentityApplicationException.class, ex ->
                        assertThat(ex.code()).isEqualTo("SCHOOL_ADMIN_CONFIGURATION_INSUFFICIENT"));

        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void restoreSuspendedSchoolWithTwoAdmins() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.SUSPENDED)));
        when(governanceQueries.findSchool(id)).thenReturn(Optional.of(detail(id, 2)));

        assertThat(service.restore(id, "issue resolved").status()).isEqualTo("NORMAL");

        verifyAuditAction("SCHOOL_RESTORE");
    }

    @Test
    void disableSupportsNormalAndSuspendedSchools() {
        UUID normalId = UUID.randomUUID();
        UUID suspendedId = UUID.randomUUID();
        when(repository.findByIdForUpdate(new SchoolId(normalId)))
                .thenReturn(Optional.of(school(normalId, SchoolStatus.NORMAL)));
        when(repository.findByIdForUpdate(new SchoolId(suspendedId)))
                .thenReturn(Optional.of(school(suspendedId, SchoolStatus.SUSPENDED)));

        assertThat(service.disable(normalId, "operations ended").status()).isEqualTo("DISABLED");
        assertThat(service.disable(suspendedId, "operations ended").status()).isEqualTo("DISABLED");

        verify(repository, org.mockito.Mockito.times(2)).save(any());
        verify(audit, org.mockito.Mockito.times(2)).record(any());
    }

    @Test
    void reEnableReturnsSchoolToPendingEnableRatherThanNormal() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.DISABLED)));

        assertThat(service.reEnable(id, "school reapplied").status()).isEqualTo("PENDING_ENABLE");

        verifyAuditAction("SCHOOL_REENABLE");
        verifyNoInteractions(governanceQueries);
    }

    @Test
    void invalidTransitionReturnsStableBusinessCode() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.NORMAL)));

        assertThatThrownBy(() -> service.activate(id, "duplicate activation"))
                .isInstanceOfSatisfying(IdentityApplicationException.class, ex ->
                        assertThat(ex.code()).isEqualTo("INVALID_SCHOOL_STATE_TRANSITION"));

        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void blankOrOneCharacterReasonIsRejectedBeforeLocking() {
        assertThatThrownBy(() -> service.disable(UUID.randomUUID(), " "))
                .isInstanceOfSatisfying(IdentityApplicationException.class, ex ->
                        assertThat(ex.code()).isEqualTo("SCHOOL_LIFECYCLE_REASON_INVALID"));
        assertThatThrownBy(() -> service.suspend(UUID.randomUUID(), "x"))
                .isInstanceOfSatisfying(IdentityApplicationException.class, ex ->
                        assertThat(ex.code()).isEqualTo("SCHOOL_LIFECYCLE_REASON_INVALID"));

        verifyNoInteractions(repository, audit, governanceQueries);
    }

    @Test
    void missingSchoolReturnsSchoolNotFound() {
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(UUID.randomUUID(), "valid reason"))
                .isInstanceOfSatisfying(IdentityApplicationException.class, ex ->
                        assertThat(ex.code()).isEqualTo("SCHOOL_NOT_FOUND"));

        verify(repository, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void repositoryFailureDoesNotWriteSuccessAudit() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(school(id, SchoolStatus.NORMAL)));
        doThrow(new RuntimeException("save failed")).when(repository).save(any());

        assertThatThrownBy(() -> service.suspend(id, "valid reason"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("save failed");

        verifyNoInteractions(audit);
    }

    @Test
    void rejectsDirectInvocationWithoutPlatformGovernanceAuthority() {
        when(authorization.requireSuperAdmin()).thenThrow(
                new IdentityApplicationException("PLATFORM_GOVERNANCE_DENIED", "denied")
        );

        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(IdentityApplicationException.class);

        verifyNoInteractions(repository);
    }

    private void verifyAuditAction(String action) {
        var captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(action);
        assertThat(captor.getValue().actorId()).isEqualTo(actorId);
    }

    private School school(UUID id, SchoolStatus status) {
        var school = School.create(new School.Builder()
                .id(new SchoolId(id))
                .name("test")
                .unifiedCodeType("USCC")
                .unifiedCode("123")
                .internalCode("INT-" + id.toString().substring(0, 8))
                .schoolType("PRIMARY")
                .region("Beijing")
                .address("addr")
                .contactName("name")
                .contactPhone("phone")
                .contactEmail("email"));
        if (status == SchoolStatus.NORMAL) {
            school.activate();
        } else if (status == SchoolStatus.SUSPENDED) {
            school.activate();
            school.suspend("pause");
        } else if (status == SchoolStatus.DISABLED) {
            school.activate();
            school.disable("disable");
        }
        school.clearDomainEvents();
        return school;
    }

    private SchoolGovernanceDetailResult detail(UUID id, long activeAdmins) {
        return new SchoolGovernanceDetailResult(
                id, "test", "PENDING_ENABLE", "INT", "USCC", "123", "PRIMARY",
                "Beijing", "addr", "name", "phone", "email", activeAdmins,
                Instant.now(), Instant.now()
        );
    }
}
