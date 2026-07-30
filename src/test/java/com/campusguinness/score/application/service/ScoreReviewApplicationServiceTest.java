package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.score.application.exception.ScoreConfigurationException;
import com.campusguinness.score.application.exception.ScoreReviewConflictException;
import com.campusguinness.score.application.exception.ScoreReviewNotFoundException;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.port.ScoreReviewContextPort;
import com.campusguinness.score.application.port.ScoreReviewRecordPort;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreReviewApplicationServiceTest {
    @Mock ScoreAttemptRepository attempts;
    @Mock ScoreReviewContextPort contexts;
    @Mock ScoreReviewRecordPort records;
    @Mock SchoolMembershipQueryPort memberships;

    private ScoreReviewApplicationService service;
    private final UUID schoolId = UUID.randomUUID();
    private final UUID otherSchoolId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final UUID entrantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ScoreReviewApplicationService(attempts, contexts, records, memberships);
    }

    @Test
    void differentSchoolAdminCanApprovePendingScore() {
        ScoreAttempt target = pending(integer(100), entrantId);
        happy(target, context("LAST", "HIGHER_BETTER", null));

        service.approve(target.id().value(), reviewerId, " ok ", null);

        assertThat(target.status()).isEqualTo(AttemptStatus.APPROVED);
        assertThat(target.isCurrentEffective()).isTrue();
        verify(attempts).save(target);
        verify(records).append(any());
    }

    @Test
    void entrantCannotReviewOwnScore() {
        ScoreAttempt target = pending(integer(100), reviewerId);
        when(memberships.findActiveSchoolAdminSchoolId(reviewerId)).thenReturn(Optional.of(schoolId));
        when(attempts.findByIdForUpdate(target.id())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.approve(target.id().value(), reviewerId, null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(attempts, never()).save(any());
        verify(records, never()).append(any());
    }

    @Test
    void inactiveSchoolAdminCannotReview() {
        when(memberships.findActiveSchoolAdminSchoolId(reviewerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), reviewerId, null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(attempts, never()).findByIdForUpdate(any());
        verify(records, never()).append(any());
    }

    @Test
    void adminFromOtherSchoolCannotReview() {
        ScoreAttempt target = pending(integer(100), entrantId);
        when(memberships.findActiveSchoolAdminSchoolId(reviewerId))
                .thenReturn(Optional.of(otherSchoolId));
        when(attempts.findByIdForUpdate(target.id())).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.approve(target.id().value(), reviewerId, null, null))
                .isInstanceOf(ScoreReviewNotFoundException.class);
        verify(attempts, never()).save(any());
        verify(records, never()).append(any());
    }

    @Test
    void draftScoreCannotBeApproved() {
        ScoreAttempt target = score(integer(100), entrantId, AttemptStatus.DRAFT, false);
        stubMembershipAndTarget(target);
        assertThatThrownBy(() -> service.approve(target.id().value(), reviewerId, null, null))
                .isInstanceOf(ScoreReviewConflictException.class);
        verify(attempts, never()).save(any());
    }

    @Test
    void approvedScoreCannotBeApprovedAgain() {
        ScoreAttempt target = score(integer(100), entrantId, AttemptStatus.APPROVED, true);
        stubMembershipAndTarget(target);
        assertThatThrownBy(() -> service.approve(target.id().value(), reviewerId, null, null))
                .isInstanceOf(ScoreReviewConflictException.class);
        verify(records, never()).append(any());
    }

    @Test
    void pendingScoreCanBeRejected() {
        ScoreAttempt target = pending(integer(100), entrantId);
        happy(target, context("BEST", "HIGHER_BETTER", null));
        service.reject(target.id().value(), reviewerId, " wrong ", " retry ");
        assertThat(target.status()).isEqualTo(AttemptStatus.REJECTED);
        assertThat(target.isCurrentEffective()).isFalse();
        verify(attempts).save(target);
    }

    @Test
    void rejectReasonIsRequired() {
        assertThatThrownBy(() -> service.reject(
                UUID.randomUUID(), reviewerId, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attempts, never()).findByIdForUpdate(any());
        verify(records, never()).append(any());
    }

    @Test
    void reviewRecordWrittenOnApprove() {
        ScoreAttempt target = pending(integer(100), entrantId);
        happy(target, context("LAST", "HIGHER_BETTER", null));
        service.approve(target.id().value(), reviewerId, " accepted ", null);
        var captor = ArgumentCaptor.forClass(ScoreReviewRecordPort.ScoreReviewRecord.class);
        verify(records).append(captor.capture());
        assertThat(captor.getValue().reviewResult()).isEqualTo("APPROVED");
        assertThat(captor.getValue().reviewComment()).isEqualTo("accepted");
    }

    @Test
    void reviewRecordWrittenOnReject() {
        ScoreAttempt target = pending(integer(100), entrantId);
        happy(target, context("LAST", "HIGHER_BETTER", null));
        service.reject(target.id().value(), reviewerId, " invalid ", " redo ");
        var captor = ArgumentCaptor.forClass(ScoreReviewRecordPort.ScoreReviewRecord.class);
        verify(records).append(captor.capture());
        assertThat(captor.getValue().reviewResult()).isEqualTo("REJECTED");
        assertThat(captor.getValue().rejectReason()).isEqualTo("invalid");
        assertThat(captor.getValue().reviewComment()).isEqualTo("redo");
    }

    @Test
    void failedReviewDoesNotWriteHistory() {
        ScoreAttempt target = score(integer(100), entrantId, AttemptStatus.REJECTED, false);
        stubMembershipAndTarget(target);
        assertThatThrownBy(() -> service.reject(target.id().value(), reviewerId, "again", null))
                .isInstanceOf(ScoreReviewConflictException.class);
        verify(records, never()).append(any());
    }

    @Test
    void lastRuleMakesNewScoreEffective() {
        ScoreAttempt target = pending(integer(100), entrantId);
        happy(target, context("LAST", "HIGHER_BETTER", null));
        service.approve(target.id().value(), reviewerId, null, null);
        assertThat(target.isCurrentEffective()).isTrue();
    }

    @Test
    void lastRuleClearsPreviousEffectiveWithoutInvalidating() {
        ScoreAttempt target = pending(integer(100), entrantId);
        ScoreAttempt current = approved(integer(90));
        happy(target, context("LAST", "HIGHER_BETTER", null));
        when(attempts.findCurrentEffectiveForUpdate(projectId, studentId))
                .thenReturn(Optional.of(current));
        service.approve(target.id().value(), reviewerId, null, null);
        assertThat(current.status()).isEqualTo(AttemptStatus.APPROVED);
        assertThat(current.isCurrentEffective()).isFalse();
        assertThat(target.isCurrentEffective()).isTrue();
        verify(attempts).save(current);
    }

    @Test
    void bestRuleKeepsBetterExistingScore() {
        ScoreAttempt target = pending(integer(90), entrantId);
        ScoreAttempt current = approved(integer(100));
        happyWithCurrent(target, current, context("BEST", "HIGHER_BETTER", null));
        service.approve(target.id().value(), reviewerId, null, null);
        assertThat(current.isCurrentEffective()).isTrue();
        assertThat(target.isCurrentEffective()).isFalse();
    }

    @Test
    void bestRulePromotesStrictlyBetterNewScore() {
        ScoreAttempt target = pending(decimal("10.5"), entrantId);
        ScoreAttempt current = approved(decimal("9.5"));
        happyWithCurrent(target, current, context("BEST", "HIGHER_BETTER", null));
        service.approve(target.id().value(), reviewerId, null, null);
        assertThat(current.isCurrentEffective()).isFalse();
        assertThat(target.isCurrentEffective()).isTrue();
    }

    @Test
    void bestRuleKeepsExistingScoreOnTie() {
        ScoreAttempt target = pending(integer(100), entrantId);
        ScoreAttempt current = approved(integer(100));
        happyWithCurrent(target, current, context("BEST", "HIGHER_BETTER", null));
        service.approve(target.id().value(), reviewerId, null, null);
        assertThat(current.isCurrentEffective()).isTrue();
        assertThat(target.isCurrentEffective()).isFalse();
    }

    @Test
    void adminDesignatedTruePromotesNewScore() {
        ScoreAttempt target = pending(integer(50), entrantId);
        ScoreAttempt current = approved(integer(100));
        happyWithCurrent(target, current, context("ADMIN_DESIGNATED", "HIGHER_BETTER", null));
        service.approve(target.id().value(), reviewerId, null, true);
        assertThat(current.isCurrentEffective()).isFalse();
        assertThat(target.isCurrentEffective()).isTrue();
    }

    @Test
    void adminDesignatedFalseKeepsExistingScore() {
        ScoreAttempt target = pending(integer(200), entrantId);
        ScoreAttempt current = approved(integer(100));
        happyWithCurrent(target, current, context("ADMIN_DESIGNATED", "HIGHER_BETTER", null));
        service.approve(target.id().value(), reviewerId, null, false);
        assertThat(current.isCurrentEffective()).isTrue();
        assertThat(target.isCurrentEffective()).isFalse();
    }

    @Test
    void noRankingKeepsExistingEffectiveScore() {
        ScoreAttempt target = pending(integer(200), entrantId);
        ScoreAttempt current = approved(integer(100));
        happyWithCurrent(target, current, context("BEST", "NO_RANKING", null));
        service.approve(target.id().value(), reviewerId, null, null);
        assertThat(current.isCurrentEffective()).isTrue();
        assertThat(target.isCurrentEffective()).isFalse();
    }

    @Test
    void invalidGradeOrderRejectsReview() {
        ScoreAttempt target = pending(grade("A"), entrantId);
        ScoreAttempt current = approved(grade("B"));
        happyWithCurrent(target, current, context("BEST", "GRADE_ORDER", "A,,B"));
        assertThatThrownBy(() -> service.approve(target.id().value(), reviewerId, null, null))
                .isInstanceOf(ScoreConfigurationException.class);
        verify(attempts, never()).save(any());
        verify(records, never()).append(any());
    }

    private void happy(
            ScoreAttempt target, ScoreReviewContextPort.ReviewContext context) {
        stubMembershipAndTarget(target);
        var typedContext = new ScoreReviewContextPort.ReviewContext(
                target.id().value(), context.schoolId(), context.activityProjectId(),
                context.studentId(), target.scoreStorageType().name(),
                context.effectiveScoreRule(), context.comparisonDirection(), context.gradeOrder());
        when(contexts.findReviewContext(target.id().value(), schoolId))
                .thenReturn(Optional.of(typedContext));
        org.mockito.Mockito.lenient().when(
                attempts.findCurrentEffectiveForUpdate(projectId, studentId))
                .thenReturn(Optional.empty());
    }

    private void happyWithCurrent(
            ScoreAttempt target,
            ScoreAttempt current,
            ScoreReviewContextPort.ReviewContext context) {
        happy(target, context);
        when(attempts.findCurrentEffectiveForUpdate(projectId, studentId))
                .thenReturn(Optional.of(current));
    }

    private void stubMembershipAndTarget(ScoreAttempt target) {
        when(memberships.findActiveSchoolAdminSchoolId(reviewerId)).thenReturn(Optional.of(schoolId));
        when(attempts.findByIdForUpdate(target.id())).thenReturn(Optional.of(target));
    }

    private ScoreReviewContextPort.ReviewContext context(
            String rule, String direction, String gradeOrder) {
        return new ScoreReviewContextPort.ReviewContext(
                UUID.randomUUID(), schoolId, projectId, studentId,
                null, rule, direction, gradeOrder);
    }

    private ScoreAttempt pending(ScoreValue value, UUID enteredBy) {
        return score(value, enteredBy, AttemptStatus.PENDING_REVIEW, false);
    }

    private ScoreAttempt approved(ScoreValue value) {
        return score(value, entrantId, AttemptStatus.APPROVED, true);
    }

    private ScoreAttempt score(
            ScoreValue value, UUID enteredBy, AttemptStatus status, boolean currentEffective) {
        ScoreStorageType type = switch (value) {
            case ScoreValue.IntegerScore ignored -> ScoreStorageType.INTEGER;
            case ScoreValue.DecimalScore ignored -> ScoreStorageType.DECIMAL;
            case ScoreValue.DurationScore ignored -> ScoreStorageType.DURATION;
            case ScoreValue.GradeScore ignored -> ScoreStorageType.GRADE;
        };
        return ScoreAttempt.reconstitute(new ScoreAttempt.Builder()
                        .id(new ScoreAttemptId(UUID.randomUUID()))
                        .schoolId(schoolId)
                        .activityProjectId(projectId)
                        .studentId(studentId)
                        .attemptNumber(1)
                        .scoreStorageType(type)
                        .scoreValue(value)
                        .scoreBusinessTime(Instant.now())
                        .enteredBy(enteredBy),
                status, currentEffective,
                status == AttemptStatus.DRAFT ? null : Instant.now(), false);
    }

    private static ScoreValue integer(long value) {
        return new ScoreValue.IntegerScore(value);
    }

    private static ScoreValue decimal(String value) {
        return new ScoreValue.DecimalScore(new BigDecimal(value));
    }

    private static ScoreValue grade(String value) {
        return new ScoreValue.GradeScore(value);
    }
}
