package com.campusguinness.interfaces.web.scoreappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/score-appeals")
public class ScoreAppealController {

    private final ScoreAppealApplicationService service;
    private final CurrentActor currentActor;
    private final JdbcTemplate jdbc;

    public ScoreAppealController(ScoreAppealApplicationService service, CurrentActor currentActor, JdbcTemplate jdbc) {
        this.service = service;
        this.currentActor = currentActor;
        this.jdbc = jdbc;
    }

    @PostMapping
    public ResponseEntity<ScoreAppealResponse> submit(@Valid @RequestBody SubmitScoreAppealRequest req) {
        UUID actorId = currentActor.requireUserId();
        UUID ownerId = resolveScoreOwner(req.scoreAttemptId());
        AuthorizationPolicy.requireResourceOwner(actorId, ownerId);
        ScoreAppealResult r = service.submit(req.schoolId(), req.scoreAttemptId(), req.studentId(), req.appealType(), req.appealReason());
        return ResponseEntity.created(URI.create("/api/v1/score-appeals/" + r.id()))
                .body(new ScoreAppealResponse(r.id(), r.status()));
    }

    /** Resolve the student who owns the score attempt — the legitimate appeal subject. */
    private UUID resolveScoreOwner(UUID scoreAttemptId) {
        var rows = jdbc.queryForList(
                "SELECT student_id FROM score_attempts WHERE id = ?", UUID.class, scoreAttemptId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("ScoreAttempt not found: " + scoreAttemptId);
        }
        return rows.getFirst();
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
