package com.campusguinness.score.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ScoreAttempt aggregate")
class ScoreAttemptTest {

    private ScoreAttempt.Builder validBuilder() {
        return new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID())
                .activityProjectId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .attemptNumber(1)
                .scoreStorageType(ScoreStorageType.INTEGER)
                .scoreValue(new ScoreValue.IntegerScore(100))
                .scoreBusinessTime(Instant.parse("2026-09-01T10:00:00Z"))
                .timeSource("老师确认")
                .enteredBy(UUID.randomUUID());
    }

    private ScoreAttempt createDraft() {
        return ScoreAttempt.create(validBuilder());
    }

    private ScoreAttempt createSubmitted() {
        var s = createDraft();
        s.submit();
        return s;
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("CG-SCORE-001: creates in DRAFT status")
        void shouldCreateInDraftStatus() {
            var s = createDraft();
            assertThat(s.status()).isEqualTo(AttemptStatus.DRAFT);
        }

        @Test
        @DisplayName("isCurrentEffective defaults to false")
        void shouldDefaultCurrentEffectiveToFalse() {
            var s = createDraft();
            assertThat(s.isCurrentEffective()).isFalse();
        }

        @Test
        @DisplayName("isManualMakeup defaults to false")
        void shouldDefaultManualMakeupToFalse() {
            var s = createDraft();
            assertThat(s.isManualMakeup()).isFalse();
        }

        @Test
        @DisplayName("manual makeup flag is retained from the creation command")
        void shouldRetainManualMakeupFlag() {
            var s = ScoreAttempt.create(validBuilder().manualMakeup(true));
            assertThat(s.isManualMakeup()).isTrue();
        }

        @Test
        @DisplayName("CG-SCORE-010: null id rejected")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> ScoreAttempt.create(validBuilder().id(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCORE-010: null schoolId rejected")
        void shouldRejectNullSchoolId() {
            assertThatThrownBy(() -> ScoreAttempt.create(validBuilder().schoolId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCORE-010: null studentId rejected")
        void shouldRejectNullStudentId() {
            assertThatThrownBy(() -> ScoreAttempt.create(validBuilder().studentId(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCORE-012: attemptNumber must be > 0")
        void shouldRejectNonPositiveAttemptNumber() {
            assertThatThrownBy(() -> ScoreAttempt.create(validBuilder().attemptNumber(0)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCORE-011: INTEGER type accepts IntegerScore")
        void shouldAcceptIntegerScore() {
            var s = ScoreAttempt.create(validBuilder()
                    .scoreStorageType(ScoreStorageType.INTEGER)
                    .scoreValue(new ScoreValue.IntegerScore(42)));
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.IntegerScore.class);
        }

        @Test
        @DisplayName("CG-SCORE-011: DECIMAL type accepts DecimalScore")
        void shouldAcceptDecimalScore() {
            var s = ScoreAttempt.create(validBuilder()
                    .scoreStorageType(ScoreStorageType.DECIMAL)
                    .scoreValue(new ScoreValue.DecimalScore(new BigDecimal("98.76"))));
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.DecimalScore.class);
        }

        @Test
        @DisplayName("CG-SCORE-011: DURATION type accepts DurationScore")
        void shouldAcceptDurationScore() {
            var s = ScoreAttempt.create(validBuilder()
                    .scoreStorageType(ScoreStorageType.DURATION)
                    .scoreValue(new ScoreValue.DurationScore(12500)));
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.DurationScore.class);
        }

        @Test
        @DisplayName("CG-SCORE-011: GRADE type accepts GradeScore")
        void shouldAcceptGradeScore() {
            var s = ScoreAttempt.create(validBuilder()
                    .scoreStorageType(ScoreStorageType.GRADE)
                    .scoreValue(new ScoreValue.GradeScore("优秀")));
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.GradeScore.class);
        }

        @Test
        @DisplayName("CG-SCORE-011: INTEGER type rejects DecimalScore")
        void shouldRejectMismatchedScoreType() {
            assertThatThrownBy(() -> ScoreAttempt.create(validBuilder()
                    .scoreStorageType(ScoreStorageType.INTEGER)
                    .scoreValue(new ScoreValue.DecimalScore(BigDecimal.ONE))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ScoreValue invariants")
    class ScoreValueInvariants {

        @Test
        @DisplayName("IntegerScore rejects negative value")
        void shouldRejectNegativeIntegerScore() {
            assertThatThrownBy(() -> new ScoreValue.IntegerScore(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("IntegerScore accepts zero")
        void shouldAcceptZeroIntegerScore() {
            var s = new ScoreValue.IntegerScore(0);
            assertThat(s.value()).isEqualTo(0);
        }

        @Test
        @DisplayName("DecimalScore rejects null")
        void shouldRejectNullDecimalScore() {
            assertThatThrownBy(() -> new ScoreValue.DecimalScore(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("DurationScore rejects negative value")
        void shouldRejectNegativeDuration() {
            assertThatThrownBy(() -> new ScoreValue.DurationScore(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("DurationScore accepts zero")
        void shouldAcceptZeroDuration() {
            var s = new ScoreValue.DurationScore(0);
            assertThat(s.durationMs()).isEqualTo(0);
        }

        @Test
        @DisplayName("GradeScore rejects null")
        void shouldRejectNullGrade() {
            assertThatThrownBy(() -> new ScoreValue.GradeScore(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("GradeScore rejects blank")
        void shouldRejectBlankGrade() {
            assertThatThrownBy(() -> new ScoreValue.GradeScore("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Field mutation (DRAFT only)")
    class FieldMutation {

        @Test
        @DisplayName("CG-SCORE-014: updateScoreValue allowed in DRAFT")
        void shouldAllowUpdateScoreValueInDraft() {
            var s = createDraft();
            s.updateScoreValue(new ScoreValue.IntegerScore(200));
            assertThat(((ScoreValue.IntegerScore) s.scoreValue()).value()).isEqualTo(200);
        }

        @Test
        @DisplayName("CG-SCORE-014: updateScoreValue rejected after submit")
        void shouldRejectUpdateScoreValueAfterSubmit() {
            var s = createSubmitted();
            assertThatThrownBy(() -> s.updateScoreValue(new ScoreValue.IntegerScore(200)))
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-SCORE-011: updateScoreValue rejects mismatched type")
        void shouldRejectMismatchedTypeOnUpdate() {
            var s = createDraft();
            assertThatThrownBy(() -> s.updateScoreValue(new ScoreValue.GradeScore("优")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        @Test
        @DisplayName("CG-SCORE-001: DRAFT → PENDING_REVIEW")
        void shouldSubmitFromDraft() {
            var s = createDraft();
            s.submit();
            assertThat(s.status()).isEqualTo(AttemptStatus.PENDING_REVIEW);
            assertThat(s.submittedAt()).isNotNull();
            assertThat(s.domainEvents()).anyMatch(e -> e instanceof ScoreAttemptSubmitted);
        }

        @Test
        @DisplayName("CG-SCORE-002: PENDING_REVIEW → APPROVED")
        void shouldApproveFromPendingReview() {
            var s = createSubmitted();
            s.approveForReview();
            assertThat(s.status()).isEqualTo(AttemptStatus.APPROVED);
            assertThat(s.isCurrentEffective()).isFalse();
            assertThat(s.domainEvents()).anyMatch(e -> e instanceof ScoreAttemptApproved);
        }

        @Test
        @DisplayName("review-only approval changes status without selecting effective score")
        void shouldApproveForReviewWithoutChangingEffectiveFlag() {
            var s = createSubmitted();
            s.approveForReview();
            assertThat(s.status()).isEqualTo(AttemptStatus.APPROVED);
            assertThat(s.isCurrentEffective()).isFalse();
            assertThat(s.domainEvents()).anyMatch(e -> e instanceof ScoreAttemptApproved);
        }

        @Test
        @DisplayName("CG-SCORE-003: PENDING_REVIEW → REJECTED")
        void shouldRejectFromPendingReview() {
            var s = createSubmitted();
            s.reject("成绩与实际不符");
            assertThat(s.status()).isEqualTo(AttemptStatus.REJECTED);
            assertThat(s.domainEvents()).anyMatch(e -> e instanceof ScoreAttemptRejected);
        }

        @Test
        @DisplayName("CG-SCORE-003: reject requires reason")
        void shouldRequireReasonOnReject() {
            var s = createSubmitted();
            assertThatThrownBy(() -> s.reject(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> s.reject("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-SCORE-004: REJECTED → DRAFT")
        void shouldReturnToDraftFromRejected() {
            var s = createSubmitted();
            s.reject("需要修改");
            s.returnToDraft();
            assertThat(s.status()).isEqualTo(AttemptStatus.DRAFT);
            assertThat(s.submittedAt()).isNull();
            assertThat(s.domainEvents()).anyMatch(e -> e instanceof ScoreAttemptReturnedToDraft);
        }

        @Test
        @DisplayName("CG-SCORE-005: APPROVED → INVALIDATED (terminal, clears isCurrentEffective)")
        void shouldInvalidateFromApproved() {
            var s = createSubmitted();
            s.approveForReview();
            s.markCurrentEffective();
            UUID replacementId = UUID.randomUUID();
            s.invalidate(replacementId);
            assertThat(s.status()).isEqualTo(AttemptStatus.INVALIDATED);
            assertThat(s.isCurrentEffective()).isFalse();
            assertThat(s.domainEvents()).anyMatch(e -> e instanceof ScoreAttemptInvalidated);
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("CG-SCORE-006: DRAFT → APPROVED rejected")
        void shouldRejectDirectApproveFromDraft() {
            var s = createDraft();
            assertThatThrownBy(s::approveForReview)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-SCORE-007: DRAFT → REJECTED rejected")
        void shouldRejectDirectRejectFromDraft() {
            var s = createDraft();
            assertThatThrownBy(() -> s.reject("reason"))
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-SCORE-008: INVALIDATED → any rejected")
        void shouldRejectTransitionFromInvalidated() {
            var s = createSubmitted();
            s.approveForReview();
            s.invalidate(UUID.randomUUID());
            assertThatThrownBy(s::submit)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
            assertThatThrownBy(s::approveForReview)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-SCORE-009: APPROVED → REJECTED rejected")
        void shouldRejectRejectFromApproved() {
            var s = createSubmitted();
            s.approveForReview();
            assertThatThrownBy(() -> s.reject("reason"))
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
            assertThatThrownBy(s::approveForReview)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("REJECTED -> APPROVED is rejected for review-only approval")
        void shouldRejectReviewApprovalFromRejected() {
            var s = createSubmitted();
            s.reject("needs revision");
            assertThatThrownBy(s::approveForReview)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("APPROVED → DRAFT rejected")
        void shouldRejectReturnToDraftFromApproved() {
            var s = createSubmitted();
            s.approveForReview();
            assertThatThrownBy(s::returnToDraft)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("PENDING_REVIEW → DRAFT rejected (must go via REJECTED)")
        void shouldRejectReturnToDraftFromPendingReview() {
            var s = createSubmitted();
            assertThatThrownBy(s::returnToDraft)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("DRAFT → INVALIDATED rejected")
        void shouldRejectInvalidateFromDraft() {
            var s = createDraft();
            assertThatThrownBy(() -> s.invalidate(UUID.randomUUID()))
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }

        @Test
        @DisplayName("only APPROVED attempts can become current effective")
        void shouldRequireApprovedStatusForEffectiveFlag() {
            var draft = createDraft();
            assertThatThrownBy(draft::markCurrentEffective)
                    .isInstanceOf(InvalidScoreAttemptStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var s = createDraft();
            assertThatThrownBy(() -> s.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
