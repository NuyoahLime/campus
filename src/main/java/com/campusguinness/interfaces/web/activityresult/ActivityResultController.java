package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.application.service.ActivityResultApplicationService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity-results")
public class ActivityResultController {

    private final ActivityResultApplicationService service;

    public ActivityResultController(ActivityResultApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ActivityResultResponse> publish(@PathVariable UUID id) {
        ActivityResultResult r = service.publishInternal(id);
        return ResponseEntity.ok(new ActivityResultResponse(r.id(), r.internalStatus(), r.publicStatus()));
    }
}
