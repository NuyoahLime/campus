package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.score.application.query.model.ScoreEntryParticipantOption;
import com.campusguinness.score.application.query.model.ScoreEntryProjectOption;
import com.campusguinness.score.application.query.port.SchoolAdminScoreEntryQueryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/score-entry")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminScoreEntryOptionController {
    private final SchoolAdminScoreEntryQueryPort queryPort;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort memberships;

    public SchoolAdminScoreEntryOptionController(
            SchoolAdminScoreEntryQueryPort queryPort,
            CurrentActor currentActor,
            SchoolMembershipQueryPort memberships) {
        this.queryPort = queryPort;
        this.currentActor = currentActor;
        this.memberships = memberships;
    }

    @GetMapping("/projects")
    public ResponseEntity<PageResponse<ScoreEntryProjectOption>> projects(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SchoolAdminScoreEntryController.validatePagination(page, size);
        String normalizedKeyword = SchoolAdminScoreEntryController.normalizeKeyword(keyword);
        var result = queryPort.findProjectOptions(
                requireSchoolId(), normalizedKeyword, page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/projects/{activityProjectId}/participants")
    public ResponseEntity<PageResponse<ScoreEntryParticipantOption>> participants(
            @PathVariable UUID activityProjectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SchoolAdminScoreEntryController.validatePagination(page, size);
        String normalizedKeyword = SchoolAdminScoreEntryController.normalizeKeyword(keyword);
        var result = queryPort.findParticipantOptions(
                requireSchoolId(), activityProjectId, normalizedKeyword, page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    private UUID requireSchoolId() {
        return memberships.findActiveSchoolAdminSchoolId(currentActor.requireUserId())
                .orElseThrow(() -> new AccessDeniedException(
                        "No active SCHOOL_ADMIN membership"));
    }
}
