package com.campusguinness.interfaces.web.feedback;

import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.CurrentActorContext;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedbacks")
public class FeedbackController {

    private final FeedbackApplicationService service;
    private final CurrentActor currentActor;
    private final CurrentActorContext actorContext;

    public FeedbackController(FeedbackApplicationService service, CurrentActor currentActor,
            CurrentActorContext actorContext) {
        this.service = service;
        this.currentActor = currentActor;
        this.actorContext = actorContext;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public List<FeedbackResult> list(@RequestParam(required = false) UUID schoolId) {
        return service.listManageable(actorContext.require(), schoolId);
    }

    @GetMapping("/mine")
    public List<FeedbackResult> listMine() {
        return service.listMine(currentActor.requireUserId());
    }

    @GetMapping("/mine/{id}")
    public ResponseEntity<FeedbackResponse> getMine(@PathVariable UUID id) {
        FeedbackResult r = service.getMine(id, currentActor.requireUserId());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> submit(@Valid @RequestBody SubmitFeedbackRequest req) {
        FeedbackResult r = service.submit(actorContext.require(), req.feedbackType(), req.content());
        return ResponseEntity.created(URI.create("/api/v1/feedbacks/" + r.id()))
                .body(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/begin-processing")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<FeedbackResponse> beginProcessing(@PathVariable UUID id) {
        FeedbackResult r = service.beginProcessing(id, actorContext.require());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<FeedbackResponse> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveFeedbackRequest req) {
        FeedbackResult r = service.resolve(id, actorContext.require(), req.reply());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<FeedbackResponse> close(@PathVariable UUID id, @Valid @RequestBody CloseFeedbackRequest req) {
        FeedbackResult r = service.close(id, actorContext.require(), req.reason());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }
}
