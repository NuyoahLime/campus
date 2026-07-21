package com.campusguinness.interfaces.web.scoreappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.appeal.application.service.ScoreAppealCorrectionService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/score-appeals")
public class ScoreAppealController {

    private final ScoreAppealApplicationService service;
    private final ScoreAppealCorrectionService correctionService;
    private final CurrentActor currentActor;

    public ScoreAppealController(ScoreAppealApplicationService service,
                                  ScoreAppealCorrectionService correctionService,
                                  CurrentActor currentActor) {
        this.service = service;
        this.correctionService = correctionService;
        this.currentActor = currentActor;
    }

    @PostMapping
    public ResponseEntity<ScoreAppealResponse> submit(@Valid @RequestBody SubmitScoreAppealRequest req) {
        ScoreAppealResult r = service.submitAuthorized(currentActor.requireUserId(),
                req.schoolId(), req.scoreAttemptId(), req.studentId(), req.appealType(), req.appealReason());
        return ResponseEntity.created(URI.create("/api/v1/score-appeals/" + r.id()))
                .body(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/begin-processing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ScoreAppealResponse> beginProcessing(@PathVariable UUID id) {
        ScoreAppealResult r = service.beginProcessing(id, currentActor.requireUserId());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<ScoreAppealResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectScoreAppealRequest req) {
        ScoreAppealResult r = service.reject(id, req.resolution());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ScoreAppealResponse> withdraw(@PathVariable UUID id) {
        ScoreAppealResult r = service.withdraw(id);
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/correct-and-resolve")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ScoreAppealResponse> correctAndResolve(
            @PathVariable UUID id,
            @Valid @RequestBody CorrectAndResolveScoreAppealRequest req) {
        ScoreValue correctedValue = toScoreValue(req);
        correctionService.correctAndResolve(id, correctedValue, req.resolution(), currentActor.requireUserId());
        return ResponseEntity.ok(new ScoreAppealResponse(id, "RESOLVED"));
    }

    private static ScoreValue toScoreValue(CorrectAndResolveScoreAppealRequest req) {
        ScoreStorageType type = ScoreStorageType.valueOf(req.scoreStorageType());
        return switch (type) {
            case INTEGER -> new ScoreValue.IntegerScore(req.integerValue() != null ? req.integerValue() : 0);
            case DECIMAL -> new ScoreValue.DecimalScore(req.decimalValue() != null ? req.decimalValue() : BigDecimal.ZERO);
            case DURATION -> new ScoreValue.DurationScore(req.durationMs() != null ? req.durationMs() : 0);
            case GRADE -> new ScoreValue.GradeScore(req.grade() != null ? req.grade() : "");
        };
    }
}
