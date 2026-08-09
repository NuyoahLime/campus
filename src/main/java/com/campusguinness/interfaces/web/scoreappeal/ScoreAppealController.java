package com.campusguinness.interfaces.web.scoreappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/score-appeals")
public class ScoreAppealController {

    private final ScoreAppealApplicationService service;

    public ScoreAppealController(ScoreAppealApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ScoreAppealResponse> submit(@Valid @RequestBody SubmitScoreAppealRequest req) {
        ScoreAppealResult r = service.submit(req.schoolId(), req.scoreAttemptId(), req.appealType(), req.appealReason());
        return ResponseEntity.created(URI.create("/api/v1/score-appeals/" + r.id()))
                .body(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/begin-processing")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ScoreAppealResponse> beginProcessing(@PathVariable UUID id, @Valid @RequestBody BeginProcessingRequest req) {
        ScoreAppealResult r = service.beginProcessing(id, req.handlerId());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ScoreAppealResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectScoreAppealRequest req) {
        ScoreAppealResult r = service.reject(id, req.resolution());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ScoreAppealResponse> withdraw(@PathVariable UUID id) {
        ScoreAppealResult r = service.withdraw(id);
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }
}
