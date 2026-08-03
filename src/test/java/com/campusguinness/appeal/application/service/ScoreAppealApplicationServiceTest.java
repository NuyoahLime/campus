package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.infrastructure.security.ActorContext;
import com.campusguinness.score.application.port.ScoreAttemptRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAppealApplicationServiceTest {
    @Mock ScoreAppealRepository repo;
    @Mock ScoreAttemptRepository scoreAttemptRepo;
    @InjectMocks ScoreAppealApplicationService svc;

    private static final UUID APPEAL_ID = UUID.randomUUID();
    private static final UUID SCHOOL_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    private ScoreAppeal appeal;

    @BeforeEach
    void setup() {
        appeal = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(APPEAL_ID))
                .schoolId(SCHOOL_ID).scoreAttemptId(UUID.randomUUID()).studentId(STUDENT_ID)
                .appealType("SCORE").appealReason("r"));
    }

    private ActorContext schoolAdmin() { return new ActorContext(ADMIN_ID, "SCHOOL_ADMIN", SCHOOL_ID); }
    private ActorContext otherSchoolAdmin() { return new ActorContext(UUID.randomUUID(), "SCHOOL_ADMIN", UUID.randomUUID()); }
    private ActorContext superAdmin() { return new ActorContext(UUID.randomUUID(), "SUPER_ADMIN", null); }
    private ActorContext studentActor() { return new ActorContext(STUDENT_ID, "STUDENT", SCHOOL_ID); }
    private ActorContext teacherActor() { return new ActorContext(UUID.randomUUID(), "TEACHER", SCHOOL_ID); }

    // ── submit ──
    @Nested class Submit {
        @Test void success() {
            UUID attemptId = UUID.randomUUID();
            var attempt = mock(com.campusguinness.score.internal.domain.ScoreAttempt.class);
            when(attempt.schoolId()).thenReturn(SCHOOL_ID);
            when(scoreAttemptRepo.findByIdAndStudentId(attemptId, STUDENT_ID)).thenReturn(Optional.of(attempt));
            assertThat(svc.submit(attemptId, STUDENT_ID, "SCORE", "r").status()).isEqualTo("SUBMITTED");
            verify(repo).save(any());
            verify(scoreAttemptRepo).findByIdAndStudentId(attemptId, STUDENT_ID);
        }
        @Test void rejectsForeignScoreAttempt() {
            when(scoreAttemptRepo.findByIdAndStudentId(any(), any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.submit(UUID.randomUUID(), UUID.randomUUID(), "SCORE", "r"))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
    }

    // ── begin-processing ──
    @Nested class BeginProcessing {
        @Test void schoolAdminSameSchool() {
            when(repo.findByIdAndSchoolId(APPEAL_ID, SCHOOL_ID)).thenReturn(Optional.of(appeal));
            svc.beginProcessing(APPEAL_ID, schoolAdmin());
            verify(repo).findByIdAndSchoolId(APPEAL_ID, SCHOOL_ID);
            verify(repo).save(any());
        }
        @Test void schoolAdminCrossSchoolReturns404() {
            when(repo.findByIdAndSchoolId(eq(APPEAL_ID), any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.beginProcessing(APPEAL_ID, otherSchoolAdmin()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
        @Test void superAdminBypassesSchoolCheck() {
            when(repo.findById(new ScoreAppealId(APPEAL_ID))).thenReturn(Optional.of(appeal));
            svc.beginProcessing(APPEAL_ID, superAdmin());
            verify(repo, never()).findByIdAndSchoolId(any(), any());
            verify(repo).save(any());
        }
        @Test void studentRoleRejected() {
            assertThatThrownBy(() -> svc.beginProcessing(APPEAL_ID, studentActor()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
        @Test void teacherRoleRejected() {
            assertThatThrownBy(() -> svc.beginProcessing(APPEAL_ID, teacherActor()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }
    }

    // ── reject (requires PROCESSING state) ──
    @Nested class Reject {
        private ScoreAppeal processingAppeal;

        @BeforeEach
        void setupProcessing() {
            processingAppeal = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(APPEAL_ID))
                    .schoolId(SCHOOL_ID).scoreAttemptId(UUID.randomUUID()).studentId(STUDENT_ID)
                    .appealType("SCORE").appealReason("r"));
            processingAppeal.beginProcessing(ADMIN_ID); // transition to PROCESSING
        }

        @Test void schoolAdminSameSchool() {
            when(repo.findByIdAndSchoolId(APPEAL_ID, SCHOOL_ID)).thenReturn(Optional.of(processingAppeal));
            svc.reject(APPEAL_ID, schoolAdmin(), "no");
            verify(repo).findByIdAndSchoolId(APPEAL_ID, SCHOOL_ID);
            verify(repo).save(any());
        }
        @Test void schoolAdminCrossSchoolReturns404() {
            when(repo.findByIdAndSchoolId(eq(APPEAL_ID), any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.reject(APPEAL_ID, otherSchoolAdmin(), "no"))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
        @Test void superAdminBypassesSchoolCheck() {
            when(repo.findById(new ScoreAppealId(APPEAL_ID))).thenReturn(Optional.of(processingAppeal));
            svc.reject(APPEAL_ID, superAdmin(), "no");
            verify(repo, never()).findByIdAndSchoolId(any(), any());
            verify(repo).save(any());
        }
    }

    // ── withdraw ──
    @Nested class Withdraw {
        @Test void success() {
            when(repo.findByIdAndStudentId(APPEAL_ID, STUDENT_ID)).thenReturn(Optional.of(appeal));
            assertThat(svc.withdraw(APPEAL_ID, STUDENT_ID).status()).isEqualTo("WITHDRAWN");
            verify(repo).save(any());
        }
        @Test void foreignAppealReturns404() {
            when(repo.findByIdAndStudentId(any(), any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.withdraw(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
    }
}
