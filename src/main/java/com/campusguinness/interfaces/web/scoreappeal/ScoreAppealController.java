package com.campusguinness.interfaces.web.scoreappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/score-appeals")
public class ScoreAppealController {

    private final ScoreAppealApplicationService service;
    private final CurrentActor currentActor;

    public ScoreAppealController(ScoreAppealApplicationService service, CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @PostMapping
    public ResponseEntity<ScoreAppealResponse> submit(@Valid @RequestBody SubmitScoreAppealRequest req) {
        ScoreAppealResult r = service.submit(req.schoolId(), req.scoreAttemptId(), req.studentId(), req.appealType(), req.appealReason());
        return ResponseEntity.created(URI.create("/api/v1/score-appeals/" + r.id()))
                .body(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/begin-processing")
    public ResponseEntity<ScoreAppealResponse> beginProcessing(@PathVariable UUID id) {
        ScoreAppealResult r = service.beginProcessing(id, currentActor.requireUserId());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ScoreAppealResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectScoreAppealRequest req) {
        ScoreAppealResult r = service.reject(id, req.resolution());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ScoreAppealResponse> withdraw(@PathVariable UUID id) {
        ScoreAppealResult r = service.withdraw(id);
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }
}
