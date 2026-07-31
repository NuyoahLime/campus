package com.campusguinness.interfaces.web.achievement;

import com.campusguinness.achievement.application.service.AchievementRecordService;
import com.campusguinness.achievement.application.query.model.PublicAchievementVerification;
import com.campusguinness.achievement.application.service.PublicAchievementVerificationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AchievementRecordController {
    private final AchievementRecordService service;
    private final PublicAchievementVerificationService publicService;
    private final CurrentActor currentActor;

    public AchievementRecordController(
            AchievementRecordService service,
            PublicAchievementVerificationService publicService,
            CurrentActor currentActor) {
        this.service = service;
        this.publicService = publicService;
        this.currentActor = currentActor;
    }

    @PostMapping("/activity-projects/{activityProjectId}/achievement-records")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminRecord> issue(@PathVariable UUID activityProjectId,
                                              @RequestBody IssueRequest req) {
        var r = service.issue(activityProjectId, req.rankingEntryId(), currentActor.requireUserId());
        return ResponseEntity.ok(new AdminRecord(r.id(), r.activityProjectId(), r.studentId(),
                r.rank(), r.scoreValue(), r.storageType(), r.title(), r.verificationCode(),
                r.status(), r.issuedAt(), r.revokedAt()));
    }

    @GetMapping("/activity-projects/{activityProjectId}/achievement-records")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<AdminRecord> listProject(@PathVariable UUID activityProjectId) {
        return service.listByProject(activityProjectId).stream().map(r -> new AdminRecord(
                r.id(), r.activityProjectId(), r.studentId(), r.rank(), r.scoreValue(),
                r.storageType(), r.title(), r.verificationCode(), r.status(), r.issuedAt(), r.revokedAt())).toList();
    }

    @GetMapping("/achievement-records/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudentRecord> listMine() {
        return service.listMine(currentActor.requireUserId()).stream().map(r -> new StudentRecord(
                r.id(), r.title(), r.rank(), r.scoreValue(), r.verificationCode(),
                r.status(), r.issuedAt(), r.revokedAt())).toList();
    }

    @GetMapping("/achievement-records/mine/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentRecord> getMine(@PathVariable UUID id) {
        return service.getMine(id, currentActor.requireUserId())
                .map(r -> ResponseEntity.ok(new StudentRecord(r.id(), r.title(), r.rank(),
                        r.scoreValue(), r.verificationCode(), r.status(), r.issuedAt(), r.revokedAt())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping({
            "/public/achievement-records/{verificationCode}",
            "/public/achievement-records/verify/{verificationCode}"})
    public PublicAchievementVerification verify(
            @PathVariable String verificationCode) {
        return publicService.verify(verificationCode);
    }

    public record IssueRequest(UUID rankingEntryId) {}
    public record AdminRecord(UUID id, UUID activityProjectId, UUID studentId, int rank,
            String scoreValue, String storageType, String title, String verificationCode,
            String status, Instant issuedAt, Instant revokedAt) {}
    public record StudentRecord(UUID id, String title, int rank, String scoreValue,
            String verificationCode, String status, Instant issuedAt, Instant revokedAt) {}
}
