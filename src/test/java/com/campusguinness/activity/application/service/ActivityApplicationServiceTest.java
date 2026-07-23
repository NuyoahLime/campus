package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityApplicationServiceTest {
    @Mock ActivityApplicationRepository appRepo;
    @Mock ActivityRepository activityRepo;
    @Mock SchoolMembershipQueryPort membershipPort;
    ActivityApplicationService svc;

    UUID userId = UUID.randomUUID();
    UUID schoolId = UUID.randomUUID();

    @BeforeEach void setUp() {
        svc = new ActivityApplicationService(appRepo, activityRepo, membershipPort);
    }

    @Nested class Submit {
        @Test void shouldSubmitWithActiveTeacherMembership() {
            when(membershipPort.hasActiveTeacherMembership(userId, schoolId)).thenReturn(true);
            var cmd = new SubmitActivityApplicationCommand(schoolId, "t", "d");
            var r = svc.submit(cmd, userId);
            assertThat(r.status()).isEqualTo("SUBMITTED");
            verify(appRepo).save(any());
        }

        @Test void shouldRejectWhenNoActiveTeacherMembership() {
            when(membershipPort.hasActiveTeacherMembership(userId, schoolId)).thenReturn(false);
            var cmd = new SubmitActivityApplicationCommand(schoolId, "t", "d");
            assertThatThrownBy(() -> svc.submit(cmd, userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("membership");
            verify(appRepo, never()).save(any());
        }
    }

    @Nested class Approve {
        @Test void shouldApproveAndCreateActivity() {
            var app = submitted(schoolId, userId);
            when(appRepo.findById(any())).thenReturn(Optional.of(app));
            UUID reviewerId = UUID.randomUUID();

            var r = svc.approve(app.id().value(), reviewerId);
            assertThat(r.status()).isEqualTo("APPROVED");
            assertThat(r.createdActivityId()).isNotNull();

            ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
            verify(activityRepo).save(activityCaptor.capture());
            Activity created = activityCaptor.getValue();
            assertThat(created.schoolId()).isEqualTo(schoolId);
            assertThat(created.title()).isEqualTo("t");
            assertThat(created.createdBy()).isEqualTo(userId);
            assertThat(created.executionStatus()).isEqualTo(ExecutionStatus.DRAFT);
            assertThat(created.publicStatus()).isEqualTo(PublicStatus.NOT_SUBMITTED);

            verify(appRepo).save(any());
        }

        @Test void shouldRejectDuplicateApproval() {
            var app = submitted(schoolId, userId);
            app.approve(UUID.randomUUID(), UUID.randomUUID()); // already approved
            when(appRepo.findById(any())).thenReturn(Optional.of(app));

            assertThatThrownBy(() -> svc.approve(app.id().value(), UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already has a created Activity");
            verify(activityRepo, never()).save(any());
        }

        @Test void shouldRejectWhenNotSubmitted() {
            var app = draft(schoolId, userId);
            when(appRepo.findById(any())).thenReturn(Optional.of(app));

            assertThatThrownBy(() -> svc.approve(app.id().value(), UUID.randomUUID()))
                    .isInstanceOf(InvalidActivityApplicationStateTransitionException.class);
            // Activity save is called but rolled back by @Transactional
            verify(appRepo, never()).save(any());
        }
    }

    @Nested class Reject {
        @Test void shouldRejectSubmitted() {
            var app = submitted(schoolId, userId);
            when(appRepo.findById(any())).thenReturn(Optional.of(app));
            UUID reviewerId = UUID.randomUUID();

            var r = svc.reject(app.id().value(), reviewerId, "reason");
            assertThat(r.status()).isEqualTo("REJECTED");
            assertThat(r.rejectReason()).isEqualTo("reason");
            verify(appRepo).save(any());
            verify(activityRepo, never()).save(any());
        }
    }

    @Nested class Withdraw {
        @Test void shouldWithdrawOwnApplication() {
            var app = submitted(schoolId, userId);
            when(appRepo.findByIdAndApplicantId(app.id().value(), userId)).thenReturn(Optional.of(app));

            var r = svc.withdraw(app.id().value(), userId);
            assertThat(r.status()).isEqualTo("WITHDRAWN");
            verify(appRepo).save(any());
        }

        @Test void shouldReturnNotFoundForOtherUser() {
            when(appRepo.findByIdAndApplicantId(any(), eq(userId))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> svc.withdraw(UUID.randomUUID(), userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested class ReturnToDraft {
        @Test void shouldReturnToDraftAndIncrementVersion() {
            var app = submitted(schoolId, userId);
            app.reject(UUID.randomUUID(), "reason");
            when(appRepo.findByIdAndApplicantId(app.id().value(), userId)).thenReturn(Optional.of(app));

            var r = svc.returnToDraft(app.id().value(), userId);
            assertThat(r.status()).isEqualTo("DRAFT");
            assertThat(r.applicationVersion()).isEqualTo(2);
            verify(appRepo).save(any());
        }

        @Test void shouldRejectReturnToDraftForOtherUser() {
            when(appRepo.findByIdAndApplicantId(any(), eq(userId))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.returnToDraft(UUID.randomUUID(), userId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested class Resubmit {
        @Test void shouldResubmitDraft() {
            var app = submitted(schoolId, userId);
            app.reject(UUID.randomUUID(), "reason");
            app.returnToDraft();
            when(appRepo.findByIdAndApplicantId(app.id().value(), userId)).thenReturn(Optional.of(app));

            var r = svc.resubmit(app.id().value(), userId);
            assertThat(r.status()).isEqualTo("SUBMITTED");
            verify(appRepo).save(any());
        }
    }

    @Nested class ListMine {
        @Test void shouldReturnOwnApplications() {
            var app = submitted(schoolId, userId);
            when(appRepo.findByApplicantId(userId)).thenReturn(java.util.List.of(app));

            var results = svc.listMine(userId);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).applicationId()).isEqualTo(app.id().value());
        }
    }

    @Nested class GetMine {
        @Test void shouldReturnOwnApplication() {
            var app = submitted(schoolId, userId);
            when(appRepo.findByIdAndApplicantId(app.id().value(), userId)).thenReturn(Optional.of(app));

            var r = svc.getMine(app.id().value(), userId);
            assertThat(r.applicationId()).isEqualTo(app.id().value());
        }
    }

    // ── Helpers ──

    private ActivityApplication draft(UUID schoolId, UUID applicantId) {
        return ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(schoolId)
                .applicantId(applicantId).title("t").description("d"));
    }

    private ActivityApplication submitted(UUID schoolId, UUID applicantId) {
        var app = draft(schoolId, applicantId);
        app.submit();
        return app;
    }
}
