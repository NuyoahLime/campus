package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.query.ActivityResultReadQueryService;
import com.campusguinness.result.application.query.model.ManagementActivityResultDetail;
import com.campusguinness.result.application.service.ActivityResultApplicationService;
import com.campusguinness.result.application.service.ActivityResultPublicationApplicationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ActivityResultController {

    private final ActivityResultApplicationService service;
    private final ActivityResultPublicationApplicationService publicationService;
    private final ActivityResultReadQueryService readQueryService;

    public ActivityResultController(
            ActivityResultApplicationService service,
            ActivityResultPublicationApplicationService publicationService,
            ActivityResultReadQueryService readQueryService) {
        this.service = service;
        this.publicationService = publicationService;
        this.readQueryService = readQueryService;
    }

    @GetMapping("/activities/{activityId}/result")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ManagementActivityResultDetail> readEditor(@PathVariable UUID activityId) {
        return ResponseEntity.ok(readQueryService.managementDetail(activityId));
    }

    @PutMapping("/activities/{activityId}/result")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ManagementActivityResultDetail> saveEditor(
            @PathVariable UUID activityId,
            @RequestBody ActivityResultSaveRequest request) {
        service.saveEditorContent(activityId, request.toCommand());
        return ResponseEntity.ok(readQueryService.managementDetail(activityId));
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

    @PostMapping("/activity-results/{id}/make-public")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResultResponse> makePublic(@PathVariable UUID id) {
        ActivityResultResult r = publicationService.makePublic(id);
        return ResponseEntity.ok(new ActivityResultResponse(r.id(), r.internalStatus(), r.publicStatus()));
    }
}
