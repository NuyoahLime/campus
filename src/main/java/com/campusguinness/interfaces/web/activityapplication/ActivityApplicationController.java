package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
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
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ActivityApplicationResponse> submit(@Valid @RequestBody SubmitActivityApplicationRequest req) {
        var cmd = new SubmitActivityApplicationCommand(req.schoolId(), req.title(), req.description());
        ActivityApplicationResult r = service.submit(cmd, currentActor.requireUserId());
        return ResponseEntity.created(URI.create("/api/v1/activity-applications/" + r.applicationId()))
                .body(ActivityApplicationResponse.from(r));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<ActivityApplicationResponse> listMine() {
        return service.listMine(currentActor.requireUserId()).stream()
                .map(ActivityApplicationResponse::from)
                .toList();
    }

    @GetMapping("/mine/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ActivityApplicationResponse> getMine(@PathVariable UUID id) {
        ActivityApplicationResult r = service.getMine(id, currentActor.requireUserId());
        return ResponseEntity.ok(ActivityApplicationResponse.from(r));
    }

    @PostMapping("/mine/{id}/withdraw")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ActivityApplicationResponse> withdraw(@PathVariable UUID id) {
        ActivityApplicationResult r = service.withdraw(id, currentActor.requireUserId());
        return ResponseEntity.ok(ActivityApplicationResponse.from(r));
    }

    @PostMapping("/mine/{id}/return-to-draft")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ActivityApplicationResponse> returnToDraft(@PathVariable UUID id) {
        ActivityApplicationResult r = service.returnToDraft(id, currentActor.requireUserId());
        return ResponseEntity.ok(ActivityApplicationResponse.from(r));
    }

    @PostMapping("/mine/{id}/submit")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ActivityApplicationResponse> resubmit(@PathVariable UUID id) {
        ActivityApplicationResult r = service.resubmit(id, currentActor.requireUserId());
        return ResponseEntity.ok(ActivityApplicationResponse.from(r));
    }

    @PutMapping("/mine/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ActivityApplicationResponse> updateDraft(
            @PathVariable UUID id, @Valid @RequestBody UpdateActivityApplicationRequest req) {
        ActivityApplicationResult r = service.updateDraft(id, currentActor.requireUserId(),
                req.title(), req.description());
        return ResponseEntity.ok(ActivityApplicationResponse.from(r));
    }

    @GetMapping("/mine/page")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<com.campusguinness.interfaces.web.common.PageResponse<ActivityApplicationResponse>> listMinePage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        var result = service.listMinePage(currentActor.requireUserId(), status, schoolId, keyword, page, size);
        var items = result.items().stream().map(ActivityApplicationResponse::from).toList();
        return ResponseEntity.ok(com.campusguinness.interfaces.web.common.PageResponse.of(
                items, result.page(), result.size(), result.totalElements()));
    }
}
