package com.campusguinness.interfaces.web.schooladminappeal;

import com.campusguinness.appeal.application.result.ScoreAppealResult;
import com.campusguinness.appeal.application.service.ScoreAppealApplicationService;
import com.campusguinness.appeal.application.service.ScoreAppealQueryService;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.interfaces.web.scoreappeal.RejectScoreAppealRequest;
import com.campusguinness.interfaces.web.scoreappeal.ScoreAppealResponse;
import com.campusguinness.interfaces.web.studentappeal.StudentScoreAppealResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/appeals")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminScoreAppealController {
    private final ScoreAppealApplicationService commandService;
    private final ScoreAppealQueryService queryService;

    public SchoolAdminScoreAppealController(ScoreAppealApplicationService commandService,
                                            ScoreAppealQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentScoreAppealResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listForCurrentSchoolAdmin(page, size);
        var items = result.items().stream().map(StudentScoreAppealResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentScoreAppealResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentScoreAppealResponse.from(queryService.detailForCurrentSchoolAdmin(id)));
    }

    @PostMapping("/{id}/begin-processing")
    public ResponseEntity<ScoreAppealResponse> beginProcessing(@PathVariable UUID id) {
        ScoreAppealResult result = commandService.beginProcessing(id);
        return ResponseEntity.ok(new ScoreAppealResponse(result.id(), result.status()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ScoreAppealResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectScoreAppealRequest request) {
        ScoreAppealResult result = commandService.reject(id, request.resolution());
        return ResponseEntity.ok(new ScoreAppealResponse(result.id(), result.status()));
    }
}
