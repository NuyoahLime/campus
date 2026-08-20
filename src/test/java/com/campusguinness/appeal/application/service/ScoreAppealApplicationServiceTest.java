package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScope;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.score.application.query.model.StudentScoreDetailResult;
import com.campusguinness.score.application.query.port.StudentScoreQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAppealApplicationServiceTest {
    @Mock ScoreAppealRepository repo;
    @Mock SchoolResourceAuthorization schoolAuthorization;
    @Mock StudentSchoolScopeAuthorization studentScopeAuthorization;
    @Mock StudentScoreQueryPort studentScoreQueryPort;
    ScoreAppealApplicationService svc;
    UUID actorUserId;
    UUID schoolId;

    @BeforeEach void setUp() {
        actorUserId=UUID.randomUUID();
        schoolId=UUID.randomUUID();
        lenient().when(schoolAuthorization.requireSchoolAdmin(any())).thenReturn(actorUserId);
        lenient().when(studentScopeAuthorization.requireUniqueActiveStudent())
                .thenReturn(new StudentSchoolScope(actorUserId, schoolId));
        svc = new ScoreAppealApplicationService(repo, schoolAuthorization,
                studentScopeAuthorization, studentScoreQueryPort);
    }

    private ScoreAppeal appeal() { return appeal(actorUserId, schoolId, "SCORE"); }

    private ScoreAppeal appeal(UUID studentId, UUID appealSchoolId, String appealType) {
        return ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID()))
                .schoolId(appealSchoolId).scoreAttemptId(UUID.randomUUID()).studentId(studentId)
                .appealType(appealType).appealReason("r"));
    }

    @Nested class Submit {
        @Test void success() {
            UUID scoreAttemptId = UUID.randomUUID();
            when(studentScoreQueryPort.findVisibleById(eq(scoreAttemptId), eq(actorUserId), eq(schoolId)))
                    .thenReturn(Optional.of(score(scoreAttemptId)));
            assertThat(svc.submit(UUID.randomUUID(),scoreAttemptId,"SCORE","r").status()).isEqualTo("SUBMITTED");
            var captor=forClass(ScoreAppeal.class);
            verify(repo).save(captor.capture());
            assertThat(captor.getValue().studentId()).isEqualTo(actorUserId);
            assertThat(captor.getValue().schoolId()).isEqualTo(schoolId);
        }

        @Test void hiddenOrOtherStudentScoreIsNotFound() {
            UUID scoreAttemptId = UUID.randomUUID();
            when(studentScoreQueryPort.findVisibleById(scoreAttemptId, actorUserId, schoolId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.submitForCurrentStudent(scoreAttemptId, "SCORE", "r"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Score attempt not found");
            verify(repo, never()).save(any());
        }

        @Test void rankingAppealIsRejectedBeforePersistence() {
            assertThatThrownBy(() -> svc.submitForCurrentStudent(UUID.randomUUID(), "RANKING", "r"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Only SCORE appeals may be submitted.");
            verifyNoInteractions(studentScoreQueryPort);
            verify(repo, never()).save(any());
        }
    }
    @Nested class BeginProcessing {
        @Test void success() { var a=appeal(); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.beginProcessing(a.id().value(),UUID.randomUUID()).status()).isEqualTo("PROCESSING"); verify(schoolAuthorization).requireSchoolAdmin(a.schoolId()); var captor=forClass(ScoreAppeal.class); verify(repo).save(captor.capture()); assertThat(captor.getValue().handlerId()).isEqualTo(actorUserId); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.beginProcessing(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
    @Nested class Reject {
        @Test void success() { var a=appeal(); a.beginProcessing(UUID.randomUUID()); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.reject(a.id().value(),"no").status()).isEqualTo("REJECTED"); verify(schoolAuthorization).requireSchoolAdmin(a.schoolId()); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.reject(UUID.randomUUID(),"no")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
    @Nested class Withdraw {
        @Test void success() { var a=appeal(); a.beginProcessing(UUID.randomUUID()); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.withdraw(a.id().value()).status()).isEqualTo("WITHDRAWN"); verify(studentScopeAuthorization).requireUniqueActiveStudent(); verify(repo).save(any()); }
        @Test void otherStudentIsSafelyDenied() { var a=appeal(UUID.randomUUID(), schoolId, "SCORE"); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThatThrownBy(()->svc.withdraw(a.id().value())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found"); verify(repo,never()).save(any()); }
        @Test void otherSchoolIsSafelyDenied() { var a=appeal(actorUserId, UUID.randomUUID(), "SCORE"); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThatThrownBy(()->svc.withdraw(a.id().value())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found"); verify(repo,never()).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.withdraw(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
    @Nested class Resolve {
        @Test void success() { var a=appeal(); a.beginProcessing(UUID.randomUUID()); a.acceptPendingCorrection(); a.beginScoreCorrecting(); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.resolve(a.id().value(),"done").status()).isEqualTo("RESOLVED"); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.resolve(UUID.randomUUID(),"done")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }

    private StudentScoreDetailResult score(UUID scoreAttemptId) {
        return new StudentScoreDetailResult(scoreAttemptId, UUID.randomUUID(), UUID.randomUUID(),
                "activity", "project", 1, "INTEGER", "10", "times",
                java.time.Instant.now(), "APPROVED", UUID.randomUUID(), 1, "rules");
    }
}
