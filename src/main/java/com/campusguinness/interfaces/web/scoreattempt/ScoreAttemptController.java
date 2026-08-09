package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.application.service.ScoreAttemptApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/score-attempts")
public class ScoreAttemptController {

    private final ScoreAttemptApplicationService service;

    public ScoreAttemptController(ScoreAttemptApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("denyAll()")
    public ResponseEntity<ScoreAttemptResponse> submit(@Valid @RequestBody SubmitScoreRequest req) {
        ScoreAttemptResult r = service.submit(ScoreAttemptWebMapper.toCommand(req));
        return ResponseEntity.created(URI.create("/api/v1/score-attempts/" + r.id()))
                .body(new ScoreAttemptResponse(r.id(), r.status(), r.scoreType()));
    }
}
