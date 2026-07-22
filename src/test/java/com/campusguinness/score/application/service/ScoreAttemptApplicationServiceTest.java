package com.campusguinness.score.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAttemptApplicationServiceTest {
    @Mock ScoreAttemptRepository repo;
    @Mock CurrentActor currentActor;
    @Mock SchoolMembershipResolver membershipResolver;
    ScoreAttemptApplicationService svc;

    private final UUID schoolId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ScoreAttemptApplicationService(repo, currentActor, membershipResolver);
        lenient().when(currentActor.isSuperAdmin()).thenReturn(false);
        lenient().when(currentActor.requireUserId()).thenReturn(actorId);
    }

    private ScoreAttempt pending(UUID sid) {
        var s = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID())).schoolId(sid)
                .activityProjectId(UUID.randomUUID()).studentId(UUID.randomUUID())
                .attemptNumber(1).scoreStorageType(ScoreStorageType.INTEGER)
                .scoreValue(new ScoreValue.IntegerScore(100))
                .scoreBusinessTime(Instant.now()).timeSource("teacher")
                .enteredBy(UUID.randomUUID()));
        s.submit();
        return s;
    }

    @Nested
    class Submit {
        @Test void shouldSubmit() {
            var r = svc.submit(new SubmitScoreCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    1, ScoreStorageType.INTEGER, new ScoreValue.IntegerScore(100),
                    Instant.now(), "teacher", UUID.randomUUID()));
            assertThat(r.status()).isEqualTo("PENDING_REVIEW");
            verify(repo).save(any());
        }
    }

    @Nested
    class FindBySchool {
        @Test void shouldFind() {
            when(repo.findBySchoolId(schoolId)).thenReturn(List.of(pending(schoolId)));
            assertThat(svc.findBySchool(schoolId)).hasSize(1);
        }
        @Test void shouldReturnEmpty() {
            when(repo.findBySchoolId(schoolId)).thenReturn(List.of());
            assertThat(svc.findBySchool(schoolId)).isEmpty();
        }
    }

    @Nested
    class Approve {
        @Test void shouldApproveWhenSchoolAdmin() {
            var s = pending(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(s));
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(true);
            assertThat(svc.approve(s.id().value()).status()).isEqualTo("APPROVED");
            verify(repo).save(any());
        }
        @Test void shouldApproveWhenSuperAdmin() {
            var s = pending(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(s));
            when(currentActor.isSuperAdmin()).thenReturn(true);
            assertThat(svc.approve(s.id().value()).status()).isEqualTo("APPROVED");
            verify(membershipResolver, never()).isSchoolAdmin(any(), any());
        }
        @Test void shouldDenyCrossSchool() {
            UUID other = UUID.randomUUID();
            var s = pending(other);
            when(repo.findById(any())).thenReturn(Optional.of(s));
            when(membershipResolver.isSchoolAdmin(actorId, other)).thenReturn(false);
            assertThatThrownBy(() -> svc.approve(s.id().value()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
        @Test void shouldDenyInvalidState() {
            var s = pending(schoolId);
            s.approve(); // already approved
            when(repo.findById(any())).thenReturn(Optional.of(s));
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(true);
            assertThatThrownBy(() -> svc.approve(s.id().value()))
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class Reject {
        @Test void shouldRejectWhenSchoolAdmin() {
            var s = pending(schoolId);
            when(repo.findById(any())).thenReturn(Optional.of(s));
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(true);
            assertThat(svc.reject(s.id().value(), "reason").status()).isEqualTo("REJECTED");
            verify(repo).save(any());
        }
        @Test void shouldDenyCrossSchool() {
            UUID other = UUID.randomUUID();
            var s = pending(other);
            when(repo.findById(any())).thenReturn(Optional.of(s));
            when(membershipResolver.isSchoolAdmin(actorId, other)).thenReturn(false);
            assertThatThrownBy(() -> svc.reject(s.id().value(), "reason"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
        @Test void shouldDenyInvalidState() {
            var s = pending(schoolId);
            s.approve(); // already approved
            when(repo.findById(any())).thenReturn(Optional.of(s));
            when(membershipResolver.isSchoolAdmin(actorId, schoolId)).thenReturn(true);
            assertThatThrownBy(() -> svc.reject(s.id().value(), "reason"))
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class NotFound {
        @Test void approveThrows() {
            when(repo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.approve(UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        @Test void rejectThrows() {
            when(repo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.reject(UUID.randomUUID(), "r"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
