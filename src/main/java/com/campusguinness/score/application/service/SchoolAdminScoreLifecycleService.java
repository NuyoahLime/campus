package com.campusguinness.score.application.service;

import com.campusguinness.audit.application.port.AuditRecordCommand;
import com.campusguinness.audit.application.port.AuditRecordCommandPort;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.exception.ScoreWriteException;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.port.ScoreReviewRecordPort;
import com.campusguinness.score.internal.domain.InvalidScoreAttemptStateTransitionException;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SchoolAdminScoreLifecycleService {
    private final ScoreAttemptRepository attempts;
    private final ScoreReviewRecordPort reviews;
    private final AuditRecordCommandPort audit;
    private final SchoolResourceAuthorization authorization;
    private final CurrentActor actor;

    public SchoolAdminScoreLifecycleService(ScoreAttemptRepository attempts,
                                            ScoreReviewRecordPort reviews,
                                            AuditRecordCommandPort audit,
                                            SchoolResourceAuthorization authorization,
                                            CurrentActor actor) {
        this.attempts = attempts;
        this.reviews = reviews;
        this.audit = audit;
        this.authorization = authorization;
        this.actor = actor;
    }

    public ScoreAttempt submit(UUID scoreAttemptId) {
        UUID submittedBy = actor.requireUserId();
        ScoreAttempt score = loadInSchool(scoreAttemptId);
        transition(score, "submit", score::submit);
        attempts.save(score);
        audit.record(new AuditRecordCommand(
                UUID.randomUUID(), score.schoolId(), submittedBy, "SCORE_ATTEMPT_SUBMITTED",
                "SCORE_ATTEMPT", score.id().value(), "{}", java.time.Instant.now()));
        return score;
    }

    public ScoreAttempt reject(UUID scoreAttemptId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw error("SCORE_REVIEW_REASON_REQUIRED", "Rejection reason is required.");
        }
        UUID reviewerId = actor.requireUserId();
        ScoreAttempt score = loadInSchool(scoreAttemptId);
        transition(score, "reject", () -> score.reject(reason.trim()));
        attempts.save(score);
        reviews.append(score.id().value(), reviewerId, "REJECTED", reason.trim());
        return score;
    }

    public ScoreAttempt returnToDraft(UUID scoreAttemptId) {
        actor.requireUserId();
        ScoreAttempt score = loadInSchool(scoreAttemptId);
        transition(score, "return-to-draft", score::returnToDraft);
        attempts.save(score);
        return score;
    }

    private ScoreAttempt loadInSchool(UUID scoreAttemptId) {
        if (scoreAttemptId == null) {
            throw error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found.");
        }
        UUID schoolId = requireSchool();
        ScoreAttempt score = attempts.findById(new ScoreAttemptId(scoreAttemptId))
                .orElseThrow(() -> error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found."));
        if (!schoolId.equals(score.schoolId())) {
            throw error("SCORE_SCOPE_DENIED", "Score management scope denied.");
        }
        return score;
    }

    private UUID requireSchool() {
        try {
            return authorization.requireUniqueSchoolAdminSchool();
        } catch (IdentityApplicationException ex) {
            throw error("SCORE_SCOPE_DENIED", "Score management scope denied.");
        }
    }

    private void transition(ScoreAttempt score, String operation, Runnable action) {
        try {
            action.run();
        } catch (InvalidScoreAttemptStateTransitionException ex) {
            throw error("SCORE_INVALID_STATE_TRANSITION", "Score attempt cannot " + operation
                    + " from status " + score.status() + ".");
        }
    }

    private ScoreWriteException error(String code, String message) {
        return new ScoreWriteException(code, message);
    }
}
