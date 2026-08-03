package com.campusguinness.interfaces.web.scoreappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
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

    @GetMapping("/mine")
    public List<ScoreAppealResult> listMine() {
        return service.listMine(currentActor.requireUserId());
    }

    @GetMapping("/mine/{id}")
    public ResponseEntity<ScoreAppealResponse> getMine(@PathVariable UUID id) {
        ScoreAppealResult r = service.getMine(id, currentActor.requireUserId());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ScoreAppealResponse> submit(@Valid @RequestBody SubmitScoreAppealRequest req) {
        ScoreAppealResult r = service.submit(req.scoreAttemptId(), currentActor.requireUserId(),
                req.appealType(), req.appealReason());
        return ResponseEntity.created(URI.create("/api/v1/score-appeals/" + r.id()))
                .body(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/begin-processing")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ScoreAppealResponse> beginProcessing(@PathVariable UUID id) {
        ScoreAppealResult r = service.beginProcessing(id, currentActor.requireUserId());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ScoreAppealResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectScoreAppealRequest req) {
        ScoreAppealResult r = service.reject(id, req.resolution());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ScoreAppealResponse> withdraw(@PathVariable UUID id) {
        ScoreAppealResult r = service.withdraw(id, currentActor.requireUserId());
        return ResponseEntity.ok(new ScoreAppealResponse(r.id(), r.status()));
    }
}
