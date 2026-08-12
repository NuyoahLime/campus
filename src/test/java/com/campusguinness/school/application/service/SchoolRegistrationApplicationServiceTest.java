package com.campusguinness.school.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.school.application.command.SubmitSchoolRegistrationCommand;
import com.campusguinness.school.application.port.SchoolRegistrationRepository;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchoolRegistrationApplicationService")
class SchoolRegistrationApplicationServiceTest {

    @Mock private SchoolRegistrationRepository repository;
    @Mock private PlatformGovernanceAuthorization authorization;
    private SchoolRegistrationApplicationService service;
    private UUID actorUserId;

    @BeforeEach void setUp() {
        actorUserId = UUID.randomUUID();
        lenient().when(authorization.requireSuperAdmin()).thenReturn(actorUserId);
        service = new SchoolRegistrationApplicationService(repository, authorization);
    }

    private SubmitSchoolRegistrationCommand validCmd() {
        return new SubmitSchoolRegistrationCommand("测试学校","USCC","1234567890","PRIMARY",
                "Beijing","address","张三","13800000000","test@test.com","desc","file-key");
    }

    @Nested @DisplayName("Submit")
    class Submit {
        @Test @DisplayName("submits in SUBMITTED status")
        void shouldSubmit() {
            var r = service.submit(validCmd());
            assertThat(r.status()).isEqualTo("SUBMITTED");
            verify(repository).save(any(SchoolRegistration.class));
        }
    }

    @Nested @DisplayName("Approve")
    class Approve {
        @Test @DisplayName("approves and sets createdSchoolId")
        void shouldApprove() {
            UUID id = UUID.randomUUID(), schoolId = UUID.randomUUID();
            var reg = submittedReg(id);
            when(repository.findById(any())).thenReturn(Optional.of(reg));
            var r = service.approve(id, "ok", schoolId);
            assertThat(r.status()).isEqualTo("APPROVED");
            assertThat(r.createdSchoolId()).isEqualTo(schoolId);
            var captor = forClass(SchoolRegistration.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().reviewedBy()).isEqualTo(actorUserId);
        }
    }

    @Nested @DisplayName("Reject")
    class Reject {
        @Test @DisplayName("rejects with reason")
        void shouldReject() {
            UUID id = UUID.randomUUID();
            when(repository.findById(any())).thenReturn(Optional.of(submittedReg(id)));
            var r = service.reject(id, "incomplete");
            assertThat(r.status()).isEqualTo("REJECTED");
            var captor = forClass(SchoolRegistration.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().reviewedBy()).isEqualTo(actorUserId);
        }
    }

    @Nested @DisplayName("Withdraw")
    class Withdraw {
        @Test @DisplayName("withdraws from SUBMITTED")
        void shouldWithdraw() {
            UUID id = UUID.randomUUID();
            when(repository.findById(any())).thenReturn(Optional.of(submittedReg(id)));
            var r = service.withdraw(id);
            assertThat(r.status()).isEqualTo("WITHDRAWN");
            verify(repository).save(any(SchoolRegistration.class));
        }
    }

    @Nested @DisplayName("Error cases")
    class Errors {
        @Test @DisplayName("throws when not found")
        void shouldThrowWhenNotFound() {
            when(repository.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.approve(UUID.randomUUID(), "ok", UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repository, never()).save(any());
        }
    }

    @Test
    void rejectsDirectReviewWithoutPlatformGovernanceAuthority() {
        when(authorization.requireSuperAdmin()).thenThrow(
                new IdentityApplicationException("PLATFORM_GOVERNANCE_DENIED", "denied"));

        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), "reason"))
                .isInstanceOf(IdentityApplicationException.class);

        verifyNoInteractions(repository);
    }

    private SchoolRegistration submittedReg(UUID id) {
        var reg = SchoolRegistration.create(new SchoolRegistration.Builder()
                .id(new SchoolRegistrationId(id)).schoolName("test").unifiedCodeType("USCC")
                .schoolType("PRIMARY").region("Beijing").address("addr")
                .contactName("name").contactPhone("phone").contactEmail("email"));
        reg.submit();
        return reg;
    }
}
