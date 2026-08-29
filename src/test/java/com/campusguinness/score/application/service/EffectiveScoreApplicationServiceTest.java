package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.exception.ScoreWriteException;
import com.campusguinness.score.application.port.ActivityProjectLockPort;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.port.ScoreCorrectionRecordPort;
import com.campusguinness.score.application.port.ScoreReviewRecordPort;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EffectiveScoreApplicationServiceTest {

    @Mock ScoreAttemptRepository attempts;
    @Mock ActivityProjectLockPort projects;
    @Mock ScoreReviewRecordPort reviews;
    @Mock ScoreCorrectionRecordPort corrections;
    @Mock SchoolResourceAuthorization authorization;
    @Mock CurrentActor actor;

    private final UUID schoolId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID activityProjectId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private EffectiveScoreApplicationService service;
    private Map<UUID, ScoreAttempt> attemptsById;
    private List<ScoreAttempt> attemptsInScope;

    @BeforeEach
    void setUp() {
        service = new EffectiveScoreApplicationService(
                attempts, projects, reviews, corrections, authorization, actor);
        attemptsById = new HashMap<>();
        attemptsInScope = new ArrayList<>();
        lenient().when(actor.requireUserId()).thenReturn(reviewerId);
        lenient().when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        when(attempts.findById(any(ScoreAttemptId.class))).thenAnswer(invocation ->
                Optional.ofNullable(attemptsById.get(invocation.getArgument(0, ScoreAttemptId.class).value())));
    }

    @Test
    void approvalChoosesBestIntegerAndHigherAttemptNumberBreaksTies() {
        ScoreAttempt old = approved(1, new ScoreValue.IntegerScore(100));
        old.markCurrentEffective();
        ScoreAttempt target = pending(2, new ScoreValue.IntegerScore(100));
        register(old, target);
        lock(scope("BEST", "INTEGER", "HIGHER_BETTER", null));

        ScoreAttempt result = service.approve(target.id().value());

        assertThat(result.status()).isEqualTo(AttemptStatus.APPROVED);
        assertThat(old.isCurrentEffective()).isFalse();
        assertThat(target.isCurrentEffective()).isTrue();
        verify(reviews).append(target.id().value(), reviewerId, "APPROVED", null);
    }

    @Test
    void approvalChoosesLowerDurationForBest() {
        ScoreAttempt slower = approved(1, new ScoreValue.DurationScore(12_000));
        slower.markCurrentEffective();
        ScoreAttempt faster = pending(2, new ScoreValue.DurationScore(9_500));
        register(slower, faster);
        lock(scope("BEST", "DURATION", "LOWER_BETTER", null));

        service.approve(faster.id().value());

        assertThat(slower.isCurrentEffective()).isFalse();
        assertThat(faster.isCurrentEffective()).isTrue();
    }

    @Test
    void approvalChoosesBestDecimalForHigherBetter() {
        ScoreAttempt lower = approved(1, new ScoreValue.DecimalScore(new BigDecimal("99.25")));
        lower.markCurrentEffective();
        ScoreAttempt higher = pending(2, new ScoreValue.DecimalScore(new BigDecimal("99.30")));
        register(lower, higher);
        lock(scope("BEST", "DECIMAL", "HIGHER_BETTER", null));

        service.approve(higher.id().value());

        assertThat(lower.isCurrentEffective()).isFalse();
        assertThat(higher.isCurrentEffective()).isTrue();
    }

    @Test
    void approvalChoosesHighestGradeUsingHistoricalJsonOrder() {
        ScoreAttempt bronze = approved(1, new ScoreValue.GradeScore("BRONZE"));
        ScoreAttempt gold = pending(2, new ScoreValue.GradeScore("GOLD"));
        register(bronze, gold);
        lock(scope("BEST", "GRADE", "GRADE_ORDER", "[\"GOLD\",\"SILVER\",\"BRONZE\"]"));

        service.approve(gold.id().value());

        assertThat(bronze.isCurrentEffective()).isFalse();
        assertThat(gold.isCurrentEffective()).isTrue();
    }

    @Test
    void approvalRejectsDuplicateHistoricalGrades() {
        ScoreAttempt target = pending(1, new ScoreValue.GradeScore("GOLD"));
        register(target);
        lock(scope("BEST", "GRADE", "GRADE_ORDER", "GOLD,GOLD,SILVER"));

        assertThatThrownBy(() -> service.approve(target.id().value()))
                .isInstanceOf(ScoreWriteException.class)
                .extracting(ex -> ((ScoreWriteException) ex).code())
                .isEqualTo("SCORE_RULE_INVALID");
    }

    @Test
    void approvalRejectsMalformedHistoricalGradeOrderJson() {
        ScoreAttempt target = pending(1, new ScoreValue.GradeScore("GOLD"));
        register(target);
        lock(scope("BEST", "GRADE", "GRADE_ORDER", "[\"GOLD\","));

        assertThatThrownBy(() -> service.approve(target.id().value()))
                .isInstanceOf(ScoreWriteException.class)
                .extracting(ex -> ((ScoreWriteException) ex).code())
                .isEqualTo("SCORE_RULE_INVALID");
    }

    @Test
    void approvalRejectsGradeAbsentFromHistoricalOrderEvenWhenItIsTheOnlyCandidate() {
        ScoreAttempt target = pending(1, new ScoreValue.GradeScore("PLATINUM"));
        register(target);
        lock(scope("BEST", "GRADE", "GRADE_ORDER", "[\"GOLD\",\"SILVER\"]"));

        assertThatThrownBy(() -> service.approve(target.id().value()))
                .isInstanceOf(ScoreWriteException.class)
                .extracting(ex -> ((ScoreWriteException) ex).code())
                .isEqualTo("SCORE_RULE_INVALID");
    }

    @Test
    void approvalRejectsMissingHistoricalRuleVersion() {
        ScoreAttempt target = pending(1, new ScoreValue.IntegerScore(10));
        register(target);
        when(projects.lock(eq(activityProjectId))).thenReturn(Optional.of(
                new ActivityProjectLockPort.Scope(activityProjectId, UUID.randomUUID(), null,
                        null, null, null, null, false)));

        assertThatThrownBy(() -> service.approve(target.id().value()))
                .isInstanceOf(ScoreWriteException.class)
                .extracting(ex -> ((ScoreWriteException) ex).code())
                .isEqualTo("SCORE_RULE_INVALID");
    }

    @Test
    void lastUsesHighestServerGeneratedAttemptNumber() {
        ScoreAttempt previous = approved(4, new ScoreValue.DecimalScore(new BigDecimal("99.9")));
        previous.markCurrentEffective();
        ScoreAttempt target = pending(5, new ScoreValue.DecimalScore(new BigDecimal("1.0")));
        register(previous, target);
        lock(scope("LAST", "DECIMAL", "HIGHER_BETTER", null));

        service.approve(target.id().value());

        assertThat(previous.isCurrentEffective()).isFalse();
        assertThat(target.isCurrentEffective()).isTrue();
    }

    @Test
    void adminDesignatedApprovalDoesNotSelectEffectiveScoreAndDesignationUsesCas() {
        ScoreAttempt current = approved(1, new ScoreValue.IntegerScore(10));
        current.markCurrentEffective();
        ScoreAttempt target = pending(2, new ScoreValue.IntegerScore(20));
        register(current, target);
        lock(scope("ADMIN_DESIGNATED", "INTEGER", "HIGHER_BETTER", null));

        service.approve(target.id().value());

        assertThat(target.isCurrentEffective()).isFalse();
        service.designate(target.id().value(), current.id().value());
        assertThat(current.isCurrentEffective()).isFalse();
        assertThat(target.isCurrentEffective()).isTrue();

        assertThatThrownBy(() -> service.designate(current.id().value(), current.id().value()))
                .isInstanceOf(ScoreWriteException.class)
                .extracting(ex -> ((ScoreWriteException) ex).code())
                .isEqualTo("SCORE_EFFECTIVE_CONFLICT");
    }

    @Test
    void designationIsIdempotentWhenTargetAndCasTokenMatch() {
        ScoreAttempt target = approved(1, new ScoreValue.IntegerScore(20));
        target.markCurrentEffective();
        register(target);
        lock(scope("ADMIN_DESIGNATED", "INTEGER", "HIGHER_BETTER", null));

        ScoreAttempt result = service.designate(target.id().value(), target.id().value());

        assertThat(result).isSameAs(target);
        verify(attempts, never()).save(any());
    }

    @Test
    void designationIsRejectedForAutomaticHistoricalRule() {
        ScoreAttempt target = approved(1, new ScoreValue.IntegerScore(20));
        register(target);
        lock(scope("BEST", "INTEGER", "HIGHER_BETTER", null));

        assertThatThrownBy(() -> service.designate(target.id().value(), null))
                .isInstanceOf(ScoreWriteException.class)
                .extracting(ex -> ((ScoreWriteException) ex).code())
                .isEqualTo("SCORE_RULE_INVALID");
    }

    @Test
    void invalidatingCurrentBestRecalculatesRemainingApprovedCandidate() {
        ScoreAttempt old = approved(1, new ScoreValue.IntegerScore(100));
        old.markCurrentEffective();
        ScoreAttempt fallback = approved(2, new ScoreValue.IntegerScore(90));
        register(old, fallback);
        lock(scope("BEST", "INTEGER", "HIGHER_BETTER", null));

        service.invalidate(old.id().value(), UUID.randomUUID());

        assertThat(old.status()).isEqualTo(AttemptStatus.INVALIDATED);
        assertThat(old.isCurrentEffective()).isFalse();
        assertThat(fallback.isCurrentEffective()).isTrue();
    }

    @Test
    void invalidatingCurrentLastRecalculatesHighestRemainingAttemptNumber() {
        ScoreAttempt earlier = approved(1, new ScoreValue.IntegerScore(100));
        ScoreAttempt current = approved(2, new ScoreValue.IntegerScore(50));
        current.markCurrentEffective();
        register(earlier, current);
        lock(scope("LAST", "INTEGER", "HIGHER_BETTER", null));

        service.invalidate(current.id().value(), UUID.randomUUID());

        assertThat(current.status()).isEqualTo(AttemptStatus.INVALIDATED);
        assertThat(current.isCurrentEffective()).isFalse();
        assertThat(earlier.isCurrentEffective()).isTrue();
    }

    @Test
    void invalidatingNonEffectiveAttemptPreservesCurrentEffectiveScore() {
        ScoreAttempt current = approved(1, new ScoreValue.IntegerScore(100));
        current.markCurrentEffective();
        ScoreAttempt target = approved(2, new ScoreValue.IntegerScore(50));
        register(current, target);
        lock(scope("BEST", "INTEGER", "HIGHER_BETTER", null));

        service.invalidate(target.id().value(), UUID.randomUUID());

        assertThat(target.status()).isEqualTo(AttemptStatus.INVALIDATED);
        assertThat(current.isCurrentEffective()).isTrue();
    }

    @Test
    void invalidatingCurrentAdminDesignatedScoreClearsWithoutAutomaticReplacement() {
        ScoreAttempt current = approved(1, new ScoreValue.IntegerScore(100));
        current.markCurrentEffective();
        ScoreAttempt other = approved(2, new ScoreValue.IntegerScore(90));
        register(current, other);
        lock(scope("ADMIN_DESIGNATED", "INTEGER", "HIGHER_BETTER", null));

        service.invalidate(current.id().value(), UUID.randomUUID());

        assertThat(current.status()).isEqualTo(AttemptStatus.INVALIDATED);
        assertThat(current.isCurrentEffective()).isFalse();
        assertThat(other.isCurrentEffective()).isFalse();
    }

    @Test
    void invalidatingOnlyEffectiveScoreLeavesNoCurrentScore() {
        ScoreAttempt current = approved(1, new ScoreValue.IntegerScore(100));
        current.markCurrentEffective();
        register(current);
        lock(scope("LAST", "INTEGER", "HIGHER_BETTER", null));

        service.invalidate(current.id().value(), UUID.randomUUID());

        assertThat(current.isCurrentEffective()).isFalse();
        assertThat(current.status()).isEqualTo(AttemptStatus.INVALIDATED);
    }

    @Test
    void historicalCorrectionAllocatesAfterLatestAttemptNumber() {
        ScoreAttempt old = approved(1, new ScoreValue.IntegerScore(100));
        old.markCurrentEffective();
        ScoreAttempt later = approved(2, new ScoreValue.IntegerScore(90));
        register(old, later);
        lock(scope("BEST", "INTEGER", "HIGHER_BETTER", null));

        ScoreAttempt replacement = service.replaceForCorrection(
                old, new ScoreValue.IntegerScore(200), "corrected", reviewerId);

        assertThat(replacement.attemptNumber()).isEqualTo(3);
        assertThat(replacement.replacesId()).isEqualTo(old.id().value());
        assertThat(replacement.status()).isEqualTo(AttemptStatus.APPROVED);
        assertThat(replacement.isCurrentEffective()).isTrue();
        assertThat(old.status()).isEqualTo(AttemptStatus.INVALIDATED);
        verify(corrections).append(old.id().value(), replacement.id().value(), "corrected", reviewerId);
    }

    private void lock(ActivityProjectLockPort.Scope scope) {
        when(projects.lock(eq(activityProjectId))).thenReturn(Optional.of(scope));
        lenient().when(attempts.findByStudentAndActivityProject(studentId, activityProjectId))
                .thenAnswer(invocation -> attemptsInScope);
    }

    private ActivityProjectLockPort.Scope scope(String effectiveRule, String storageType,
                                                String direction, String gradeOrder) {
        return new ActivityProjectLockPort.Scope(activityProjectId, UUID.randomUUID(), UUID.randomUUID(),
                effectiveRule, storageType, direction, gradeOrder, false);
    }

    private ScoreAttempt pending(int attemptNumber, ScoreValue value) {
        ScoreAttempt attempt = attempt(attemptNumber, value);
        attempt.submit();
        return attempt;
    }

    private ScoreAttempt approved(int attemptNumber, ScoreValue value) {
        ScoreAttempt attempt = pending(attemptNumber, value);
        attempt.approveForReview();
        return attempt;
    }

    private ScoreAttempt attempt(int attemptNumber, ScoreValue value) {
        ScoreAttempt score = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID()))
                .schoolId(schoolId)
                .activityProjectId(activityProjectId)
                .studentId(studentId)
                .attemptNumber(attemptNumber)
                .scoreStorageType(storageType(value))
                .scoreValue(value)
                .enteredBy(reviewerId));
        return score;
    }

    private ScoreStorageType storageType(ScoreValue value) {
        return switch (value) {
            case ScoreValue.IntegerScore ignored -> ScoreStorageType.INTEGER;
            case ScoreValue.DecimalScore ignored -> ScoreStorageType.DECIMAL;
            case ScoreValue.DurationScore ignored -> ScoreStorageType.DURATION;
            case ScoreValue.GradeScore ignored -> ScoreStorageType.GRADE;
        };
    }

    private void register(ScoreAttempt... values) {
        for (ScoreAttempt value : values) {
            attemptsById.put(value.id().value(), value);
            attemptsInScope.add(value);
        }
    }
}
