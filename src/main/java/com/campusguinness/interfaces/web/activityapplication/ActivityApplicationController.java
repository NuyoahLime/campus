package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.service.ActivityApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity-applications")
public class ActivityApplicationController {

    private final ActivityApplicationService service;

    public ActivityApplicationController(ActivityApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("denyAll()")
    public ResponseEntity<ActivityApplicationResponse> submit(@Valid @RequestBody SubmitActivityApplicationRequest req) {
        var cmd = new SubmitActivityApplicationCommand(req.schoolId(), req.title(), req.description());
        ActivityApplicationResult r = service.submit(cmd);
        return ResponseEntity.created(URI.create("/api/v1/activity-applications/" + r.id()))
                .body(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityApplicationResponse> approve(@PathVariable UUID id, @Valid @RequestBody ApproveActivityApplicationRequest req) {
        ActivityApplicationResult r = service.approve(id, req.activityId());
        return ResponseEntity.ok(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityApplicationResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectActivityApplicationRequest req) {
        ActivityApplicationResult r = service.reject(id, req.reason());
        return ResponseEntity.ok(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ActivityApplicationResponse> withdraw(@PathVariable UUID id) {
        ActivityApplicationResult r = service.withdraw(id);
        return ResponseEntity.ok(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }
}
