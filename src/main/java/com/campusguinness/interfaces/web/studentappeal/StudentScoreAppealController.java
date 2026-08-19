package com.campusguinness.interfaces.web.studentappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.appeal.application.service.ScoreAppealQueryService;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.interfaces.web.scoreappeal.ScoreAppealResponse;
import com.campusguinness.interfaces.web.scoreappeal.SubmitScoreAppealRequest;
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
@RequestMapping("/api/v1/student/appeals")
@PreAuthorize("hasRole('STUDENT')")
public class StudentScoreAppealController {
    private final ScoreAppealApplicationService commandService;
    private final ScoreAppealQueryService queryService;

    public StudentScoreAppealController(ScoreAppealApplicationService commandService,
                                        ScoreAppealQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<ScoreAppealResponse> submit(@Valid @RequestBody SubmitScoreAppealRequest request) {
        ScoreAppealResult result = commandService.submitForCurrentStudent(
                request.scoreAttemptId(), request.appealType(), request.appealReason());
        return ResponseEntity.created(URI.create("/api/v1/student/appeals/" + result.id()))
                .body(new ScoreAppealResponse(result.id(), result.status()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentScoreAppealResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listForCurrentStudent(page, size);
        var items = result.items().stream().map(StudentScoreAppealResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentScoreAppealResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentScoreAppealResponse.from(queryService.detailForCurrentStudent(id)));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ScoreAppealResponse> withdraw(@PathVariable UUID id) {
        ScoreAppealResult result = commandService.withdraw(id);
        return ResponseEntity.ok(new ScoreAppealResponse(result.id(), result.status()));
    }
}
