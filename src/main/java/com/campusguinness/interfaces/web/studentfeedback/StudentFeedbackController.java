package com.campusguinness.interfaces.web.studentfeedback;

import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.feedback.application.service.FeedbackQueryService;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.interfaces.web.feedback.CloseFeedbackRequest;
import com.campusguinness.interfaces.web.feedback.FeedbackResponse;
import com.campusguinness.interfaces.web.feedback.SubmitFeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/feedback")
@PreAuthorize("hasRole('STUDENT')")
public class StudentFeedbackController {
    private final FeedbackApplicationService commandService;
    private final FeedbackQueryService queryService;

    public StudentFeedbackController(FeedbackApplicationService commandService,
                                     FeedbackQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> submit(@Valid @RequestBody SubmitFeedbackRequest request) {
        FeedbackResult result = commandService.submitForCurrentStudent(request.feedbackType(), request.content());
        return ResponseEntity.created(URI.create("/api/v1/student/feedback/" + result.id()))
                .body(new FeedbackResponse(result.id(), result.status()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentFeedbackResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listForCurrentStudent(page, size);
        var items = result.items().stream().map(StudentFeedbackResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentFeedbackResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentFeedbackResponse.from(queryService.detailForCurrentStudent(id)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<FeedbackResponse> close(@PathVariable UUID id, @Valid @RequestBody CloseFeedbackRequest request) {
        FeedbackResult result = commandService.close(id, request.reason());
        return ResponseEntity.ok(new FeedbackResponse(result.id(), result.status()));
    }
}
