package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.*;

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
class ScoreAppealCorrectionServiceTest {

    @Mock ScoreAppealRepository appealRepo;
    @Mock ScoreAttemptRepository attemptRepo;
    ScoreAppealCorrectionService svc;

    @BeforeEach void setUp() { svc = new ScoreAppealCorrectionService(appealRepo, attemptRepo); }

    private ScoreAppeal processingAppeal(UUID appealId, UUID attemptId) {
        var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(appealId))
                .schoolId(UUID.randomUUID()).scoreAttemptId(attemptId).studentId(UUID.randomUUID())
                .appealType("SCORE").appealReason("wrong score"));
        a.beginProcessing(UUID.randomUUID());
        return a;
    }

    private ScoreAttempt approvedAttempt(UUID id) {
        var s = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(id)).schoolId(UUID.randomUUID())
                .activityProjectId(UUID.randomUUID()).studentId(UUID.randomUUID())
                .attemptNumber(1).scoreStorageType(ScoreStorageType.INTEGER)
                .scoreValue(new ScoreValue.IntegerScore(100)).enteredBy(UUID.randomUUID()));
        s.submit(); s.approve();
        return s;
    }

    @Nested class Success {
        @Test void correctsAndResolves() {
            UUID appealId = UUID.randomUUID(), attemptId = UUID.randomUUID();
            var appeal = processingAppeal(appealId, attemptId);
            var oldAttempt = approvedAttempt(attemptId);
            when(appealRepo.findById(any())).thenReturn(Optional.of(appeal));
            when(attemptRepo.findById(any())).thenReturn(Optional.of(oldAttempt));

            svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "corrected", UUID.randomUUID());

            assertThat(appeal.status()).isEqualTo(AppealStatus.RESOLVED);
            assertThat(oldAttempt.status()).isEqualTo(AttemptStatus.INVALIDATED);
            assertThat(oldAttempt.isCurrentEffective()).isFalse();
            verify(attemptRepo).save(oldAttempt);
            verify(attemptRepo, times(2)).save(any(ScoreAttempt.class)); // old + new
            verify(appealRepo).save(appeal);
        }
    }

    @Nested class Errors {
        @Test void rejectsWrongScoreType() {
            UUID appealId = UUID.randomUUID(), attemptId = UUID.randomUUID();
            when(appealRepo.findById(any())).thenReturn(Optional.of(processingAppeal(appealId, attemptId)));
            when(attemptRepo.findById(any())).thenReturn(Optional.of(approvedAttempt(attemptId)));

            assertThatThrownBy(() -> svc.correctAndResolve(appealId, new ScoreValue.GradeScore("A"), "r", UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(attemptRepo, never()).save(any());
        }

        @Test void rejectsNonProcessingAppeal() {
            UUID appealId = UUID.randomUUID();
            var appeal = processingAppeal(appealId, UUID.randomUUID());
            appeal.withdraw(); // now WITHDRAWN (terminal)
            when(appealRepo.findById(any())).thenReturn(Optional.of(appeal));

            assertThatThrownBy(() -> svc.correctAndResolve(appealId, new ScoreValue.IntegerScore(200), "r", UUID.randomUUID()))
                    .isInstanceOf(InvalidAppealStateTransitionException.class);
            verify(attemptRepo, never()).save(any());
        }
    }
}
