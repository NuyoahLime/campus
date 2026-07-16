package com.campusguinness.appeal.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ScoreAppeal aggregate")
class ScoreAppealTest {

    private ScoreAppeal.Builder validBuilder() {
        return new ScoreAppeal.Builder()
                .id(new ScoreAppealId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID()).scoreAttemptId(UUID.randomUUID())
                .studentId(UUID.randomUUID()).appealType("SCORE")
                .appealReason("成绩记录有误，请求复核");
    }

    private ScoreAppeal createSubmitted() { return ScoreAppeal.create(validBuilder()); }

    @Nested @DisplayName("Creation")
    class Creation {
        @Test @DisplayName("creates in SUBMITTED status")
        void shouldCreateInSubmitted() {
            assertThat(createSubmitted().status()).isEqualTo(AppealStatus.SUBMITTED);
        }
        @Test @DisplayName("null appealReason rejected")
        void shouldRejectNullReason() {
            assertThatThrownBy(() -> ScoreAppeal.create(validBuilder().appealReason(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested @DisplayName("Basic workflow: SUBMITTED → PROCESSING → ... → RESOLVED")
    class BasicWorkflow {
        @Test @DisplayName("SUBMITTED → PROCESSING")
        void shouldBeginProcessing() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            assertThat(a.status()).isEqualTo(AppealStatus.PROCESSING);
        }
        @Test @DisplayName("PROCESSING → ACCEPTED_PENDING_CORRECTION → SCORE_CORRECTING → RESOLVED")
        void shouldFollowCorrectionPathToResolved() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.acceptPendingCorrection(); a.beginScoreCorrecting();
            a.resolve("成绩已更正为正确值");
            assertThat(a.status()).isEqualTo(AppealStatus.RESOLVED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ScoreAppealResolved);
        }
        @Test @DisplayName("PROCESSING → RANK_CHECKING → RANK_FIXING → RESOLVED")
        void shouldFollowRankPathToResolved() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.beginRankChecking(); a.beginRankFixing();
            a.resolve("排行榜已修正");
            assertThat(a.status()).isEqualTo(AppealStatus.RESOLVED);
        }
        @Test @DisplayName("PROCESSING → ESCALATED → PLATFORM_PROCESSING → PLATFORM_DECIDED → RESOLVED")
        void shouldFollowEscalationPathToResolved() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.escalate(UUID.randomUUID()); a.beginPlatformProcessing();
            a.platformDecide(); a.resolve("平台裁决：申诉成立");
            assertThat(a.status()).isEqualTo(AppealStatus.RESOLVED);
        }
    }

    @Nested @DisplayName("Withdrawal paths")
    class Withdrawal {
        @Test @DisplayName("SUBMITTED → WITHDRAWN")
        void shouldWithdrawFromSubmitted() {
            var a = createSubmitted(); a.withdraw();
            assertThat(a.status()).isEqualTo(AppealStatus.WITHDRAWN);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ScoreAppealWithdrawn);
        }
        @Test @DisplayName("PROCESSING → WITHDRAWN")
        void shouldWithdrawFromProcessing() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.withdraw();
            assertThat(a.status()).isEqualTo(AppealStatus.WITHDRAWN);
        }
    }

    @Nested @DisplayName("Rejection paths")
    class Rejection {
        @Test @DisplayName("PROCESSING → REJECTED")
        void shouldRejectFromProcessing() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.reject("申诉理由不成立");
            assertThat(a.status()).isEqualTo(AppealStatus.REJECTED);
            assertThat(a.domainEvents()).anyMatch(e -> e instanceof ScoreAppealRejected);
        }
        @Test @DisplayName("RANK_CHECKING → REJECTED")
        void shouldRejectFromRankChecking() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.beginRankChecking(); a.reject("排名核查无误");
            assertThat(a.status()).isEqualTo(AppealStatus.REJECTED);
        }
    }

    @Nested @DisplayName("Platform escalation: RETURNED_TO_SCHOOL cycle")
    class EscalationCycle {
        @Test @DisplayName("PLATFORM_PROCESSING → RETURNED_TO_SCHOOL → PROCESSING")
        void shouldReturnToSchoolAndResumeProcessing() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.escalate(UUID.randomUUID()); a.beginPlatformProcessing();
            a.returnToSchool();
            assertThat(a.status()).isEqualTo(AppealStatus.RETURNED_TO_SCHOOL);
            a.beginProcessing(UUID.randomUUID());
            assertThat(a.status()).isEqualTo(AppealStatus.PROCESSING);
        }
    }

    @Nested @DisplayName("Terminal states")
    class TerminalStates {
        @Test @DisplayName("RESOLVED is terminal")
        void resolvedIsTerminal() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.acceptPendingCorrection(); a.beginScoreCorrecting();
            a.resolve("done");
            assertThatThrownBy(() -> a.beginProcessing(UUID.randomUUID()))
                    .isInstanceOf(InvalidAppealStateTransitionException.class);
        }
        @Test @DisplayName("REJECTED is terminal")
        void rejectedIsTerminal() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.reject("no");
            assertThatThrownBy(() -> a.acceptPendingCorrection())
                    .isInstanceOf(InvalidAppealStateTransitionException.class);
        }
        @Test @DisplayName("WITHDRAWN is terminal")
        void withdrawnIsTerminal() {
            var a = createSubmitted(); a.withdraw();
            assertThatThrownBy(() -> a.beginProcessing(UUID.randomUUID()))
                    .isInstanceOf(InvalidAppealStateTransitionException.class);
        }
    }

    @Nested @DisplayName("Illegal transitions")
    class IllegalTransitions {
        @Test @DisplayName("Cannot beginProcessing from terminal")
        void shouldRejectProcessingFromResolved() {
            var a = createSubmitted(); a.beginProcessing(UUID.randomUUID());
            a.acceptPendingCorrection(); a.beginScoreCorrecting(); a.resolve("ok");
            assertThatThrownBy(() -> a.beginProcessing(UUID.randomUUID()))
                    .isInstanceOf(InvalidAppealStateTransitionException.class);
        }
        @Test @DisplayName("Cannot escalate from SUBMITTED")
        void shouldRejectEscalateFromSubmitted() {
            assertThatThrownBy(() -> createSubmitted().escalate(UUID.randomUUID()))
                    .isInstanceOf(InvalidAppealStateTransitionException.class);
        }
    }

    @Nested @DisplayName("Collection protection")
    class CollectionProtection {
        @Test @DisplayName("domain events unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            assertThatThrownBy(() -> createSubmitted().domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
