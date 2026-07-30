package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.ranking.application.query.model.RankingProjectDetail;
import com.campusguinness.ranking.application.query.model.RankingProjectItem;
import com.campusguinness.ranking.application.query.model.RankingPreviewResult;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import com.campusguinness.ranking.application.query.model.RankingVersionSummary;
import com.campusguinness.ranking.application.service.SchoolAdminRankingApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/rankings")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminRankingController {

    private final SchoolAdminRankingApplicationService service;
    private final CurrentActor currentActor;

    public SchoolAdminRankingController(
            SchoolAdminRankingApplicationService service,
            CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @GetMapping("/projects")
    public PageResponse<RankingProjectItem> listProjects(
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String rankingStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.listProjects(
                currentActor.requireUserId(),
                executionStatus,
                rankingStatus,
                keyword,
                page,
                size);
        return PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/projects/{activityProjectId}")
    public RankingProjectDetail getProject(
            @PathVariable UUID activityProjectId) {
        return service.getProject(
                currentActor.requireUserId(), activityProjectId);
    }

    @GetMapping("/projects/{activityProjectId}/preview")
    public RankingPreviewResult preview(
            @PathVariable UUID activityProjectId) {
        return service.preview(
                currentActor.requireUserId(), activityProjectId);
    }

    @PostMapping("/projects/{activityProjectId}/publish")
    public ResponseEntity<RankingVersionDetail> publish(
            @PathVariable UUID activityProjectId,
            @Valid @RequestBody PublishRankingRequest request) {
        RankingVersionDetail version = service.publish(
                currentActor.requireUserId(),
                activityProjectId,
                request.expectedSourceFingerprint());
        return ResponseEntity
                .created(URI.create(
                        "/api/v1/school-admin/rankings/versions/"
                                + version.versionId()))
                .body(version);
    }

    @GetMapping("/projects/{activityProjectId}/current")
    public RankingVersionDetail getCurrent(
            @PathVariable UUID activityProjectId) {
        return service.getCurrent(
                currentActor.requireUserId(), activityProjectId);
    }

    @GetMapping("/projects/{activityProjectId}/versions")
    public PageResponse<RankingVersionSummary> getVersions(
            @PathVariable UUID activityProjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.getVersions(
                currentActor.requireUserId(), activityProjectId, page, size);
        return PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/versions/{versionId}")
    public RankingVersionDetail getVersion(@PathVariable UUID versionId) {
        return service.getVersion(currentActor.requireUserId(), versionId);
    }

    @PostMapping("/projects/{activityProjectId}/withdraw")
    public ResponseEntity<Void> withdraw(
            @PathVariable UUID activityProjectId,
            @Valid @RequestBody WithdrawRankingRequest request) {
        service.withdraw(
                currentActor.requireUserId(), activityProjectId, request.reason());
        return ResponseEntity.noContent().build();
    }

    public record PublishRankingRequest(
            @NotBlank(message = "expectedSourceFingerprint is required")
            String expectedSourceFingerprint) {
    }

    public record WithdrawRankingRequest(
            @NotBlank(message = "reason is required")
            String reason) {
    }
}
