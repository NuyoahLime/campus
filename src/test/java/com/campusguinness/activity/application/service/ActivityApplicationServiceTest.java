package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
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
class ActivityApplicationServiceTest {
    @Mock ActivityApplicationRepository repo;
    @Mock CurrentActor currentActor;
    @Mock SchoolResourceAuthorization authorization;
    ActivityApplicationService svc;
    UUID actorUserId;
    @BeforeEach void setUp() {
        actorUserId = UUID.randomUUID();
        lenient().when(currentActor.requireUserId()).thenReturn(actorUserId);
        lenient().when(authorization.requireSchoolAdmin(any())).thenReturn(actorUserId);
        svc = new ActivityApplicationService(repo, currentActor, authorization);
    }

    @Nested class Submit {
        @Test void shouldSubmit() {
            var r = svc.submit(new SubmitActivityApplicationCommand(UUID.randomUUID(), "t", "d"));
            assertThat(r.status()).isEqualTo("SUBMITTED");
            var captor = forClass(ActivityApplication.class);
            verify(repo).save(captor.capture());
            assertThat(captor.getValue().applicantId()).isEqualTo(actorUserId);
        }
    }
    @Nested class Approve {
        @Test void shouldApprove() {
            var a = submitted(); when(repo.findById(any())).thenReturn(Optional.of(a));
            UUID aid = UUID.randomUUID();
            var r = svc.approve(a.id().value(), aid);
            assertThat(r.status()).isEqualTo("APPROVED"); assertThat(r.createdActivityId()).isEqualTo(aid);
            var captor = forClass(ActivityApplication.class);
            verify(repo).save(captor.capture());
            assertThat(captor.getValue().reviewedBy()).isEqualTo(actorUserId);
            verify(authorization).requireSchoolAdmin(a.schoolId());
        }
    }
    @Nested class Reject {
        @Test void shouldReject() {
            var a = submitted(); when(repo.findById(any())).thenReturn(Optional.of(a));
            assertThat(svc.reject(UUID.randomUUID(), "reason").status()).isEqualTo("REJECTED");
            var captor = forClass(ActivityApplication.class);
            verify(repo).save(captor.capture());
            assertThat(captor.getValue().reviewedBy()).isEqualTo(actorUserId);
            verify(authorization).requireSchoolAdmin(a.schoolId());
        }
    }
    @Nested class Withdraw {
        @Test void shouldWithdraw() {
            when(repo.findById(any())).thenReturn(Optional.of(submitted()));
            assertThat(svc.withdraw(UUID.randomUUID()).status()).isEqualTo("WITHDRAWN");
            verify(repo).save(any());
        }
    }
    @Nested class Errors {
        @Test void shouldThrowWhenNotFound() {
            when(repo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.approve(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
    }
    private ActivityApplication submitted() {
        var a = ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(UUID.randomUUID())
                .applicantId(UUID.randomUUID()).title("t").description("d"));
        a.submit(); return a;
    }
}
