package com.campusguinness.interfaces.web.schooladminscore;

import com.campusguinness.score.application.port.ScoreWriteContextPort;
import com.campusguinness.score.application.service.SchoolAdminScoreDraftService;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreValue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminScoreController {
    private final SchoolAdminScoreDraftService service;

    public SchoolAdminScoreController(SchoolAdminScoreDraftService service) {
        this.service = service;
    }

    @GetMapping("/activities/{activityId}/scores")
    public ActivityScoresResponse scores(@PathVariable UUID activityId,
                                         @RequestParam(required = false) UUID activityProjectId,
                                         @RequestParam(required = false) String status) {
        var result = service.activityScores(activityId, activityProjectId, status);
        return new ActivityScoresResponse(result.activityId(), result.activityTitle(), result.activityStatus(),
                result.scores().stream().map(ScoreAttemptResponse::from).toList());
    }

    @GetMapping("/activities/{activityId}/score-candidates")
    public SchoolAdminScoreDraftService.ActivityCandidates candidates(@PathVariable UUID activityId) {
        return service.scoreCandidates(activityId);
    }

    @PostMapping("/activity-projects/{activityProjectId}/score-attempts")
    public ResponseEntity<ScoreAttemptResponse> create(@PathVariable UUID activityProjectId,
                                                       @Valid @RequestBody CreateScoreDraftRequest request) {
        ScoreAttempt score = service.createDraft(activityProjectId, request.studentId(),
                request.integerValue(), request.decimalValue(), request.durationMs(), request.grade(),
                request.scoreBusinessTime());
        return ResponseEntity.created(URI.create("/api/v1/school-admin/score-attempts/" + score.id().value()))
                .body(ScoreAttemptResponse.from(score));
    }

    @GetMapping("/score-attempts/{scoreAttemptId}")
    public ScoreAttemptResponse detail(@PathVariable UUID scoreAttemptId) {
        return ScoreAttemptResponse.from(service.scoreDetail(scoreAttemptId));
    }

    @PatchMapping("/score-attempts/{scoreAttemptId}")
    public ScoreAttemptResponse update(@PathVariable UUID scoreAttemptId,
                                       @Valid @RequestBody UpdateScoreDraftRequest request) {
        return ScoreAttemptResponse.from(service.updateDraft(scoreAttemptId, request.integerValue(),
                request.decimalValue(), request.durationMs(), request.grade(), request.scoreBusinessTime()));
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateScoreDraftRequest(@NotNull UUID studentId, Long integerValue,
                                          BigDecimal decimalValue, Long durationMs, String grade,
                                          Instant scoreBusinessTime) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateScoreDraftRequest(Long integerValue, BigDecimal decimalValue,
                                          Long durationMs, String grade, Instant scoreBusinessTime) {}

    public record ActivityScoresResponse(UUID activityId, String activityTitle, String activityStatus,
                                         List<ScoreAttemptResponse> scores) {}

    public record ScoreAttemptResponse(UUID scoreAttemptId, UUID activityId, String activityTitle,
                                       UUID activityProjectId, String projectName, UUID studentId,
                                       String studentDisplay, String studentNumber, int attemptNumber,
                                       String status, String scoreStorageType, Long integerValue,
                                       BigDecimal decimalValue, Long durationMs, String grade,
                                       Instant scoreBusinessTime) {
        static ScoreAttemptResponse from(ScoreAttempt score) {
            Long integerValue = score.scoreValue() instanceof ScoreValue.IntegerScore value ? value.value() : null;
            BigDecimal decimalValue = score.scoreValue() instanceof ScoreValue.DecimalScore value ? value.value() : null;
            Long durationMs = score.scoreValue() instanceof ScoreValue.DurationScore value ? value.durationMs() : null;
            String grade = score.scoreValue() instanceof ScoreValue.GradeScore value ? value.grade() : null;
            return new ScoreAttemptResponse(score.id().value(), null, null, score.activityProjectId(), null,
                    score.studentId(), null, null, score.attemptNumber(), score.status().name(),
                    score.scoreStorageType().name(), integerValue, decimalValue, durationMs, grade,
                    score.scoreBusinessTime());
        }

        static ScoreAttemptResponse from(ScoreWriteContextPort.ScoreRow score) {
            Long integerValue = "INTEGER".equals(score.scoreStorageType()) && score.numericValue() != null
                    ? score.numericValue().longValueExact() : null;
            BigDecimal decimalValue = "DECIMAL".equals(score.scoreStorageType()) ? score.numericValue() : null;
            return new ScoreAttemptResponse(score.scoreAttemptId(), score.activityId(), score.activityTitle(),
                    score.activityProjectId(), score.projectName(), score.studentId(), score.studentDisplay(),
                    score.studentNumber(), score.attemptNumber(), score.status(), score.scoreStorageType(),
                    integerValue, decimalValue, score.durationMs(), score.grade(), score.scoreBusinessTime());
        }
    }
}
