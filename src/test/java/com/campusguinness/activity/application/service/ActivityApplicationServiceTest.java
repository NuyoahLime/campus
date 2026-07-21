package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityApplicationServiceTest {
    @Mock ActivityApplicationRepository repo;
    @Mock CurrentActor currentActor;
    @Mock SchoolMembershipResolver membershipResolver;
    ActivityApplicationService svc;

    private final UUID schoolId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ActivityApplicationService(repo, currentActor, membershipResolver);
        lenient().when(currentActor.isSuperAdmin()).thenReturn(false);
    }

    @Nested
    class Submit {
        @Test
        void shouldSubmit() {
            var r = svc.submit(new SubmitActivityApplicationCommand(UUID.randomUUID(), UUID.randomUUID(), "t", "d"));
            assertThat(r.status()).isEqualTo("SUBMITTED");
            verify(repo).save(any());
        }
    }

    @Nested
    class Approve {
        @Test
        void shouldApproveWhenSchoolAdminOfCorrectSchool() {
            var app = submitted(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(app));
            when(membershipResolver.isSchoolAdmin(reviewerId, schoolId)).thenReturn(true);
            UUID aid = UUID.randomUUID();
            var r = svc.approve(app.id().value(), reviewerId, aid);
            assertThat(r.status()).isEqualTo("APPROVED");
            assertThat(r.createdActivityId()).isEqualTo(aid);
            verify(repo).save(any());
        }

        @Test
        void shouldApproveWhenSuperAdmin() {
            var app = submitted(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(app));
            when(currentActor.isSuperAdmin()).thenReturn(true);
            // No school membership check for SUPER_ADMIN
            var r = svc.approve(app.id().value(), reviewerId, UUID.randomUUID());
            assertThat(r.status()).isEqualTo("APPROVED");
            verify(membershipResolver, never()).isSchoolAdmin(any(), any());
        }

        @Test
        void shouldRejectWhenNotSchoolAdmin() {
            var app = submitted(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(app));
            when(membershipResolver.isSchoolAdmin(reviewerId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.approve(app.id().value(), reviewerId, UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void shouldRejectWhenAdminOfWrongSchool() {
            UUID wrongSchoolId = UUID.randomUUID();
            var app = submitted(schoolId); // app is at schoolId
            when(repo.findById(any())).thenReturn(Optional.of(app));
            // reviewer is admin at wrongSchoolId, not schoolId
            lenient().when(membershipResolver.isSchoolAdmin(reviewerId, wrongSchoolId)).thenReturn(true);
            when(membershipResolver.isSchoolAdmin(reviewerId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.approve(app.id().value(), reviewerId, UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class Reject {
        @Test
        void shouldRejectWhenSchoolAdmin() {
            var app = submitted(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(app));
            when(membershipResolver.isSchoolAdmin(reviewerId, schoolId)).thenReturn(true);
            assertThat(svc.reject(app.id().value(), reviewerId, "reason").status()).isEqualTo("REJECTED");
            verify(repo).save(any());
        }

        @Test
        void shouldRejectWhenSuperAdmin() {
            var app = submitted(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(app));
            when(currentActor.isSuperAdmin()).thenReturn(true);
            assertThat(svc.reject(app.id().value(), reviewerId, "reason").status()).isEqualTo("REJECTED");
            verify(membershipResolver, never()).isSchoolAdmin(any(), any());
        }

        @Test
        void shouldDenyWhenNotAdmin() {
            var app = submitted(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(app));
            when(membershipResolver.isSchoolAdmin(reviewerId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.reject(app.id().value(), reviewerId, "reason"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class Withdraw {
        @Test
        void shouldWithdraw() {
            when(repo.findById(any())).thenReturn(Optional.of(submitted(schoolId)));
            assertThat(svc.withdraw(UUID.randomUUID()).status()).isEqualTo("WITHDRAWN");
            verify(repo).save(any());
        }
    }

    @Nested
    class FindPendingBySchool {
        @Test
        void shouldReturnPendingForSchool() {
            var app = submitted(schoolId);
            when(repo.findBySchoolIdAndStatus(schoolId, ApplicationStatus.SUBMITTED))
                    .thenReturn(List.of(app));
            List<ActivityApplicationResult> results = svc.findPendingBySchool(schoolId);
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().status()).isEqualTo("SUBMITTED");
        }

        @Test
        void shouldReturnEmptyForSchoolWithNoPending() {
            when(repo.findBySchoolIdAndStatus(schoolId, ApplicationStatus.SUBMITTED))
                    .thenReturn(List.of());
            assertThat(svc.findPendingBySchool(schoolId)).isEmpty();
        }
    }

    @Nested
    class Errors {
        @Test
        void shouldThrowWhenNotFound() {
            when(repo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.approve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void shouldNotSaveOnFailedAuth() {
            var app = submitted(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(app));
            when(membershipResolver.isSchoolAdmin(reviewerId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.approve(app.id().value(), reviewerId, UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
    }

    private ActivityApplication submitted(UUID sid) {
        var a = ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(sid)
                .applicantId(UUID.randomUUID()).title("t").description("d"));
        a.submit();
        return a;
    }
}
