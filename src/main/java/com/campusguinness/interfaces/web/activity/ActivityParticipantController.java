package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.service.ActivityParticipantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/activities")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class ActivityParticipantController {
    private final ActivityParticipantService service;

    public ActivityParticipantController(ActivityParticipantService service) {
        this.service = service;
    }

    @GetMapping("/{activityId}/participants")
    public List<ActivityParticipantResponse> list(@PathVariable UUID activityId) {
        return service.list(activityId).stream().map(ActivityParticipantResponse::from).toList();
    }

    @GetMapping("/{activityId}/participant-candidates")
    public List<ActivityParticipantResponse> candidates(@PathVariable UUID activityId,
                                                         @RequestParam(required = false) String q) {
        return service.candidates(activityId, q).stream().map(ActivityParticipantResponse::from).toList();
    }

    @PostMapping("/{activityId}/participants")
    public ResponseEntity<Void> assign(@PathVariable UUID activityId,
                                       @Valid @RequestBody AssignParticipantRequest request) {
        service.assign(activityId, request.studentId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{activityId}/participants/{studentId}")
    public ResponseEntity<Void> remove(@PathVariable UUID activityId, @PathVariable UUID studentId) {
        service.remove(activityId, studentId);
        return ResponseEntity.noContent().build();
    }

    public record AssignParticipantRequest(@NotNull UUID studentId) {}

    public record ActivityParticipantResponse(UUID studentId, String displayName,
                                              String studentNumber, String grade,
                                              String className, java.time.Instant assignedAt) {
        static ActivityParticipantResponse from(
                com.campusguinness.activity.application.query.model.ActivityParticipantResult result) {
            return new ActivityParticipantResponse(result.studentId(), result.displayName(),
                    result.studentNumber(), result.grade(), result.className(), result.assignedAt());
        }
    }
}
