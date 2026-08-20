package com.campusguinness.interfaces.web.schooladminfeedback;

import com.campusguinness.feedback.application.result.FeedbackResult;
import com.campusguinness.feedback.application.service.FeedbackApplicationService;
import com.campusguinness.feedback.application.service.FeedbackQueryService;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.interfaces.web.feedback.FeedbackResponse;
import com.campusguinness.interfaces.web.feedback.ResolveFeedbackRequest;
import com.campusguinness.interfaces.web.studentfeedback.StudentFeedbackResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/feedback")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminFeedbackController {
    private final FeedbackApplicationService commandService;
    private final FeedbackQueryService queryService;

    public SchoolAdminFeedbackController(FeedbackApplicationService commandService,
                                         FeedbackQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentFeedbackResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listForCurrentSchoolAdmin(page, size);
        var items = result.items().stream().map(StudentFeedbackResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentFeedbackResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentFeedbackResponse.from(queryService.detailForCurrentSchoolAdmin(id)));
    }

    @PostMapping("/{id}/begin-processing")
    public ResponseEntity<FeedbackResponse> beginProcessing(@PathVariable UUID id) {
        FeedbackResult result = commandService.beginProcessing(id);
        return ResponseEntity.ok(new FeedbackResponse(result.id(), result.status()));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<FeedbackResponse> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveFeedbackRequest request) {
        FeedbackResult result = commandService.resolve(id, request.reply());
        return ResponseEntity.ok(new FeedbackResponse(result.id(), result.status()));
    }
}
