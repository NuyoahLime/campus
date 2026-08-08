package com.campusguinness.interfaces.web.feedback;

import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedbacks")
public class FeedbackController {

    private final FeedbackApplicationService service;

    public FeedbackController(FeedbackApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> submit(@Valid @RequestBody SubmitFeedbackRequest req) {
        FeedbackResult r = service.submit(req.schoolId(), req.feedbackType(), req.content());
        return ResponseEntity.created(URI.create("/api/v1/feedbacks/" + r.id()))
                .body(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/begin-processing")
    public ResponseEntity<FeedbackResponse> beginProcessing(@PathVariable UUID id, @Valid @RequestBody BeginProcessingRequest req) {
        FeedbackResult r = service.beginProcessing(id, req.handlerId());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<FeedbackResponse> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveFeedbackRequest req) {
        FeedbackResult r = service.resolve(id, req.reply());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<FeedbackResponse> close(@PathVariable UUID id, @Valid @RequestBody CloseFeedbackRequest req) {
        FeedbackResult r = service.close(id, req.reason());
        return ResponseEntity.ok(new FeedbackResponse(r.id(), r.status()));
    }
}
