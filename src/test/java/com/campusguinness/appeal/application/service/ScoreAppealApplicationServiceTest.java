package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAppealApplicationServiceTest {
    @Mock ScoreAppealRepository repo;
    @Mock JdbcTemplate jdbc;
    @Mock CurrentActor currentActor;
    @Mock SchoolMembershipResolver membershipResolver;
    ScoreAppealApplicationService svc;

    private final UUID schoolId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ScoreAppealApplicationService(repo, jdbc, currentActor, membershipResolver);
        lenient().when(currentActor.isSuperAdmin()).thenReturn(false);
        lenient().when(currentActor.requireUserId()).thenReturn(actorId);
    }

    private ScoreAppeal appeal(UUID sid, UUID stId) {
        return ScoreAppeal.create(new ScoreAppeal.Builder()
                .id(new ScoreAppealId(UUID.randomUUID())).schoolId(sid)
                .scoreAttemptId(UUID.randomUUID()).studentId(stId)
                .appealType("SCORE").appealReason("r"));
    }

    @Nested
    class Submit {
        @Test void success() {
            assertThat(svc.submit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SCORE", "r").status())
                    .isEqualTo("SUBMITTED");
            verify(repo).save(any());
        }
    }

    @Nested
    class BeginProcessing {
        @Test void success() { var a=appeal(schoolId, studentId); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.beginProcessing(a.id().value(),UUID.randomUUID()).status()).isEqualTo("PROCESSING"); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.beginProcessing(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }

    @Nested
    class Reject {
        @Test
        void shouldRejectWhenSchoolAdmin() {
            var a = appeal(schoolId, studentId);
            a.beginProcessing(actorId);
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(true);
            assertThat(svc.reject(a.id().value(), "no").status()).isEqualTo("REJECTED");
            verify(repo).save(any());
        }

        @Test
        void shouldRejectWhenSuperAdmin() {
            var a = appeal(schoolId, studentId);
            a.beginProcessing(actorId);
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(currentActor.isSuperAdmin()).thenReturn(true);
            assertThat(svc.reject(a.id().value(), "no").status()).isEqualTo("REJECTED");
            verify(membershipResolver, never()).isSchoolAdmin(any(), any());
        }

        @Test
        void shouldDenyRejectWhenNotSchoolAdmin() {
            var a = appeal(schoolId, studentId);
            a.beginProcessing(actorId);
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.reject(a.id().value(), "no"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void shouldDenyRejectWhenAdminOfOtherSchool() {
            UUID otherSchoolId = UUID.randomUUID();
            var a = appeal(schoolId, studentId);
            a.beginProcessing(actorId);
            when(repo.findById(any())).thenReturn(Optional.of(a));
            lenient().when(membershipResolver.isSchoolAdmin(actorId, otherSchoolId)).thenReturn(true);
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(false);
            assertThatThrownBy(() -> svc.reject(a.id().value(), "no"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.reject(UUID.randomUUID(),"no")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }

    @Nested
    class Withdraw {
        @Test
        void shouldWithdrawWhenOwner() {
            var a = appeal(schoolId, studentId);
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(currentActor.requireUserId()).thenReturn(studentId);
            assertThat(svc.withdraw(a.id().value()).status()).isEqualTo("WITHDRAWN");
            verify(repo).save(any());
        }

        @Test
        void shouldDenyWithdrawWhenNotOwner() {
            var a = appeal(schoolId, studentId);
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(currentActor.requireUserId()).thenReturn(actorId); // not studentId
            assertThatThrownBy(() -> svc.withdraw(a.id().value()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test
        void shouldDenyWithdrawWhenSchoolAdmin() {
            var a = appeal(schoolId, studentId);
            when(repo.findById(any())).thenReturn(Optional.of(a));
            // School admin is not the appeal owner — withdraw denied by owner check
            assertThatThrownBy(() -> svc.withdraw(a.id().value()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.withdraw(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }

    @Nested
    class Resolve {
        @Test void success() { var a=appeal(schoolId, studentId); a.beginProcessing(UUID.randomUUID()); a.acceptPendingCorrection(); a.beginScoreCorrecting(); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.resolve(a.id().value(),"done").status()).isEqualTo("RESOLVED"); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.resolve(UUID.randomUUID(),"done")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
}
