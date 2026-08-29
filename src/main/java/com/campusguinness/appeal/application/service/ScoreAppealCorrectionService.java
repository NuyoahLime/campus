package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.service.EffectiveScoreApplicationService;
import com.campusguinness.score.internal.domain.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cross-aggregate service for ScoreAppeal Path A (score correction workflow).
 *
 * <p>Orchestrates ScoreAppeal state transitions and ScoreAttempt replacement
 * in a single atomic transaction. No HTTP interface — authorization not available.
 */
@Service
@Transactional
public class ScoreAppealCorrectionService {

    private final ScoreAppealRepository appealRepo;
    private final ScoreAttemptRepository attemptRepo;
    private final EffectiveScoreApplicationService effectiveScores;

    public ScoreAppealCorrectionService(ScoreAppealRepository appealRepo, ScoreAttemptRepository attemptRepo,
                                        EffectiveScoreApplicationService effectiveScores) {
        this.appealRepo = appealRepo;
        this.attemptRepo = attemptRepo;
        this.effectiveScores = effectiveScores;
    }

    /**
     * Accept an appeal for score correction, create a replacement ScoreAttempt,
     * invalidate the original, and resolve the appeal — all atomically.
     *
     * @param appealId       the ScoreAppeal to resolve
     * @param correctedValue the corrected ScoreValue (must match old storage type)
     * @param resolution     resolution text for the appeal record
     * @param actorId        operator ID (TEMPORARY_EXPLICIT_ACTOR_ID)
     */
    public void correctAndResolve(UUID appealId, ScoreValue correctedValue, String resolution, UUID actorId) {
        // 1. Load appeal, validate starting state
        var appeal = appealRepo.findById(new ScoreAppealId(appealId))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAppeal not found: " + appealId));
        if (appeal.status() != AppealStatus.PROCESSING) {
            throw new InvalidAppealStateTransitionException(appeal.status(), "correct and resolve");
        }

        // 2. Load original ScoreAttempt
        var oldAttempt = attemptRepo.findById(new ScoreAttemptId(appeal.scoreAttemptId()))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAttempt not found: " + appeal.scoreAttemptId()));

        // 3. Validate correction value type matches
        validateScoreConsistency(oldAttempt.scoreStorageType(), correctedValue);

        // 4. Transition appeal
        appeal.acceptPendingCorrection();
        appeal.beginScoreCorrecting();

        // 5-8. The locked score service allocates the replacement number and
        // coordinates review, effective replacement, and correction persistence.
        effectiveScores.replaceForCorrection(oldAttempt, correctedValue, resolution, actorId);

        // 9. Resolve appeal
        appeal.resolve(resolution);
        appealRepo.save(appeal);
    }

    private static void validateScoreConsistency(ScoreStorageType type, ScoreValue value) {
        boolean valid = switch (type) {
            case INTEGER -> value instanceof ScoreValue.IntegerScore;
            case DECIMAL -> value instanceof ScoreValue.DecimalScore;
            case DURATION -> value instanceof ScoreValue.DurationScore;
            case GRADE -> value instanceof ScoreValue.GradeScore;
        };
        if (!valid) {
            throw new IllegalArgumentException("corrected score type does not match " + type);
        }
    }
}
