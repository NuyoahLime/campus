package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.exception.ScoreWriteException;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.query.model.ScoreReviewHistoryEntry;
import com.campusguinness.score.application.query.port.ScoreReviewHistoryQueryPort;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SchoolAdminScoreReviewHistoryService {
    private final ScoreAttemptRepository attempts;
    private final ScoreReviewHistoryQueryPort reviews;
    private final SchoolResourceAuthorization authorization;
    private final CurrentActor actor;

    public SchoolAdminScoreReviewHistoryService(ScoreAttemptRepository attempts,
                                                ScoreReviewHistoryQueryPort reviews,
                                                SchoolResourceAuthorization authorization,
                                                CurrentActor actor) {
        this.attempts = attempts;
        this.reviews = reviews;
        this.authorization = authorization;
        this.actor = actor;
    }

    public List<ScoreReviewHistoryEntry> history(UUID scoreAttemptId) {
        actor.requireUserId();
        UUID schoolId = requireSchool();
        var attempt = attempts.findById(new ScoreAttemptId(scoreAttemptId))
                .orElseThrow(() -> error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found."));
        if (!schoolId.equals(attempt.schoolId())) {
            throw error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found.");
        }
        return reviews.findByScoreAttemptId(scoreAttemptId);
    }

    private UUID requireSchool() {
        try {
            return authorization.requireUniqueSchoolAdminSchool();
        } catch (IdentityApplicationException ex) {
            throw error("SCORE_SCOPE_DENIED", "Score management scope denied.");
        }
    }

    private ScoreWriteException error(String code, String message) {
        return new ScoreWriteException(code, message);
    }
}
