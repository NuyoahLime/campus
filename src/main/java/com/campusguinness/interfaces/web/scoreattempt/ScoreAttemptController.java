package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.application.service.ScoreAttemptApplicationService;
import com.campusguinness.score.application.service.TeacherScoreEntryApplicationService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/score-attempts")
public class ScoreAttemptController {

    private final ScoreAttemptApplicationService service;
    private final TeacherScoreEntryApplicationService teacherScoreEntryService;
    private final CurrentActor currentActor;

    public ScoreAttemptController(
            ScoreAttemptApplicationService service,
            ObjectProvider<TeacherScoreEntryApplicationService> teacherScoreEntryService,
            CurrentActor currentActor) {
        this.service = service;
        this.teacherScoreEntryService = teacherScoreEntryService.getIfAvailable();
        this.currentActor = currentActor;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ScoreAttemptResult> listMine() {
        UUID studentId = currentActor.requireUserId();
        return service.findMyApprovedScores(studentId);
    }

    @GetMapping("/mine/{attemptId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ScoreAttemptResponse> getMine(@PathVariable UUID attemptId) {
        UUID studentId = currentActor.requireUserId();
        ScoreAttemptResult r = service.getMyApprovedScore(attemptId, studentId);
        return ResponseEntity.ok(new ScoreAttemptResponse(r.id(), r.status(), r.scoreType()));
    }

    @GetMapping("/mine/review-progress")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ScoreAttemptResult> listProgress() {
        UUID studentId = currentActor.requireUserId();
        return service.listMyProgress(studentId);
    }

    @GetMapping("/mine/review-progress/{attemptId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ScoreAttemptResponse> getProgress(@PathVariable UUID attemptId) {
        UUID studentId = currentActor.requireUserId();
        ScoreAttemptResult r = service.getMyProgress(attemptId, studentId);
        return ResponseEntity.ok(new ScoreAttemptResponse(r.id(), r.status(), r.scoreType()));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ScoreAttemptResponse> submit(@Valid @RequestBody SubmitScoreRequest req) {
        UUID actorId = currentActor.requireUserId();
        if (teacherScoreEntryService == null) {
            ScoreAttemptResult result = service.submit(
                    ScoreAttemptWebMapper.toCommand(req, actorId));
            return ResponseEntity.created(
                            URI.create("/api/v1/score-attempts/" + result.id()))
                    .body(new ScoreAttemptResponse(
                            result.id(), result.status(), result.scoreType()));
        }
        UUID attemptId = teacherScoreEntryService.createAndSubmitLegacy(
                actorId,
                ScoreAttemptWebMapper.toTeacherCommand(req),
                req.scoreStorageType());
        return ResponseEntity.created(URI.create("/api/v1/score-attempts/" + attemptId))
                .body(new ScoreAttemptResponse(
                        attemptId, "PENDING_REVIEW", req.scoreStorageType()));
    }
}
