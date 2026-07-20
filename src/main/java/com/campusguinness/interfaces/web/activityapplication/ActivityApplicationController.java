package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity-applications")
public class ActivityApplicationController {

    private final ActivityApplicationService service;
    private final CurrentActor currentActor;

    public ActivityApplicationController(ActivityApplicationService service, CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @PostMapping
    public ResponseEntity<ActivityApplicationResponse> submit(@Valid @RequestBody SubmitActivityApplicationRequest req) {
        var cmd = new SubmitActivityApplicationCommand(req.schoolId(), req.applicantId(), req.title(), req.description());
        ActivityApplicationResult r = service.submit(cmd);
        return ResponseEntity.created(URI.create("/api/v1/activity-applications/" + r.id()))
                .body(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ActivityApplicationResponse> approve(@PathVariable UUID id, @Valid @RequestBody ApproveActivityApplicationRequest req) {
        ActivityApplicationResult r = service.approve(id, currentActor.requireUserId(), req.activityId());
        return ResponseEntity.ok(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ActivityApplicationResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectActivityApplicationRequest req) {
        ActivityApplicationResult r = service.reject(id, currentActor.requireUserId(), req.reason());
        return ResponseEntity.ok(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ActivityApplicationResponse> withdraw(@PathVariable UUID id) {
        ActivityApplicationResult r = service.withdraw(id);
        return ResponseEntity.ok(new ActivityApplicationResponse(r.id(), r.status(), r.createdActivityId()));
    }
}
