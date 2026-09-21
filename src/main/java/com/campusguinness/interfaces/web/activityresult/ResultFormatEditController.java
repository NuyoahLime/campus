package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.format.ResultFormatEditApplicationService;
import com.campusguinness.result.application.format.ResultFormatEditResult;
import com.campusguinness.result.application.format.ResultFormatHistoryEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ResultFormatEditController {
    private final ResultFormatEditApplicationService service;
    public ResultFormatEditController(ResultFormatEditApplicationService service) { this.service = service; }
    @PostMapping("/activity-results/{resultId}/versions/{versionId}/format-edits")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ResultFormatEditResult> append(@PathVariable UUID resultId, @PathVariable UUID versionId,
                                                         @RequestBody ResultFormatEditRequest request) {
        return ResponseEntity.ok(service.append(resultId, versionId, request.reason(), request.presentation()));
    }
    @GetMapping("/school-admin/activity-results/{resultId}/versions/{versionId}/format-history")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<List<ResultFormatHistoryEntry>> history(@PathVariable UUID resultId, @PathVariable UUID versionId) {
        return ResponseEntity.ok(service.history(resultId, versionId));
    }
}
