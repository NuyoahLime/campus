package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.ranking.application.query.model.StudentCurrentRankingDetail;
import com.campusguinness.ranking.application.query.model.StudentOwnRanking;
import com.campusguinness.ranking.application.query.model.StudentRankingProjectItem;
import com.campusguinness.ranking.application.service.StudentRankingApplicationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/rankings")
@PreAuthorize("hasRole('STUDENT')")
public class StudentRankingController {

    private final StudentRankingApplicationService service;
    private final CurrentActor currentActor;

    public StudentRankingController(
            StudentRankingApplicationService service,
            CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @GetMapping
    public PageResponse<StudentRankingProjectItem> list(
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String rankingAvailability,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.listProjects(
                currentActor.requireUserId(),
                executionStatus,
                rankingAvailability,
                keyword,
                page,
                size);
        return PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/{activityProjectId}")
    public StudentCurrentRankingDetail current(
            @PathVariable UUID activityProjectId) {
        return service.getCurrentRanking(
                currentActor.requireUserId(), activityProjectId);
    }

    @GetMapping("/{activityProjectId}/mine")
    public StudentOwnRanking mine(
            @PathVariable UUID activityProjectId) {
        return service.getMyRank(
                currentActor.requireUserId(), activityProjectId);
    }
}
