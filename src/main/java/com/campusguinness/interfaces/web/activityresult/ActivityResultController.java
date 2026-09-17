package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.result.ActivityResultEditorResult;
import com.campusguinness.result.application.service.ActivityResultApplicationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ActivityResultController {

    private final ActivityResultApplicationService service;

    public ActivityResultController(ActivityResultApplicationService service) {
        this.service = service;
    }

    @GetMapping("/activities/{activityId}/result")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResultEditorResult> readEditor(@PathVariable UUID activityId) {
        return ResponseEntity.ok(service.readEditor(activityId));
    }

    @PutMapping("/activities/{activityId}/result")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResultEditorResult> saveEditor(
            @PathVariable UUID activityId,
            @RequestBody ActivityResultSaveRequest request) {
        return ResponseEntity.ok(service.saveEditorContent(activityId, request.toCommand()));
    }

    @PostMapping("/activity-results/{id}/publish")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResultResponse> publish(@PathVariable UUID id) {
        ActivityResultResult r = service.publishInternal(id);
        return ResponseEntity.ok(new ActivityResultResponse(r.id(), r.internalStatus(), r.publicStatus()));
    }

    @PostMapping("/activity-results/{id}/withdraw")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResultResponse> withdraw(@PathVariable UUID id) {
        ActivityResultResult r = service.withdrawInternal(id);
        return ResponseEntity.ok(new ActivityResultResponse(r.id(), r.internalStatus(), r.publicStatus()));
    }

    @PostMapping("/activity-results/{id}/return-to-draft")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResultResponse> returnToDraft(@PathVariable UUID id) {
        ActivityResultResult r = service.returnToDraft(id);
        return ResponseEntity.ok(new ActivityResultResponse(r.id(), r.internalStatus(), r.publicStatus()));
    }
}
