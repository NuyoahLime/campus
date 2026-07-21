package com.campusguinness.interfaces.web.feedback;

import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedbacks")
public class FeedbackController {

    private final FeedbackApplicationService service;
    private final CurrentActor currentActor;

    public FeedbackController(FeedbackApplicationService service, CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> submit(@Valid @RequestBody SubmitFeedbackRequest req) {
        FeedbackResult r = service.submit(req.schoolId(), currentActor.requireUserId(), req.feedbackType(), req.content());
        return ResponseEntity.created(URI.create("/api/v1/feedbacks/" + r.id()))
                .body(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/begin-processing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FeedbackResponse> beginProcessing(@PathVariable UUID id) {
        FeedbackResult r = service.beginProcessing(id, currentActor.requireUserId());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FeedbackResponse> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveFeedbackRequest req) {
        FeedbackResult r = service.resolve(id, req.reply());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FeedbackResponse> close(@PathVariable UUID id, @Valid @RequestBody CloseFeedbackRequest req) {
        FeedbackResult r = service.close(id, req.reason());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }
}
