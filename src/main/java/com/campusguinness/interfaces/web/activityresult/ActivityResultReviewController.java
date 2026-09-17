package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.service.ActivityResultReviewApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity-results")
public class ActivityResultReviewController {
    private final ActivityResultReviewApplicationService service;

    public ActivityResultReviewController(ActivityResultReviewApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/submit-public-review")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResultResponse> submit(@PathVariable UUID id) {
        var result = service.submit(id);
        return ResponseEntity.ok(new ActivityResultResponse(
                result.id(), result.internalStatus(), result.publicStatus()));
    }
}
