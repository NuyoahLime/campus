package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.application.service.ScoreAttemptApplicationService;

import jakarta.validation.Valid;
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
    private final CurrentActor currentActor;
    private final SchoolMembershipResolver membershipResolver;

    public ScoreAttemptController(ScoreAttemptApplicationService service, CurrentActor currentActor,
                                   SchoolMembershipResolver membershipResolver) {
        this.service = service;
        this.currentActor = currentActor;
        this.membershipResolver = membershipResolver;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public List<ScoreAttemptResult> list(@RequestParam UUID schoolId) {
        UUID actorId = currentActor.requireUserId();
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireTeacherOrAbove(membershipResolver, actorId, schoolId);
        }
        return service.findBySchool(schoolId);
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ScoreAttemptResponse> submit(@Valid @RequestBody SubmitScoreRequest req) {
        var actorId = currentActor.requireUserId();
        AuthorizationPolicy.requireTeacherOrAbove(membershipResolver, actorId, req.schoolId());
        ScoreAttemptResult r = service.submit(ScoreAttemptWebMapper.toCommand(req, actorId));
        return ResponseEntity.created(URI.create("/api/v1/score-attempts/" + r.id()))
                .body(new ScoreAttemptResponse(r.id(), r.status(), r.scoreType()));
    }
}
