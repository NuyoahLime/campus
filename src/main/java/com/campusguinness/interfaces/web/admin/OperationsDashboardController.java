package com.campusguinness.interfaces.web.admin;

import com.campusguinness.admin.application.service.SchoolOperationsOverviewService;
import com.campusguinness.admin.application.service.SchoolOperationsOverviewService.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class OperationsDashboardController {
    private final SchoolOperationsOverviewService service;

    public OperationsDashboardController(SchoolOperationsOverviewService service) {
        this.service = service;
    }

    @GetMapping("/operations/overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<DashboardResponse> overview(@RequestParam UUID schoolId) {
        Overview o = service.getOverview(schoolId);
        return ResponseEntity.ok(new DashboardResponse(o.schoolId(), o.generatedAt(),
                o.activities(), o.applications(), o.scoreAttempts(), o.scoreAppeals(),
                o.feedbacks(), o.media(), o.rankings(), o.pendingActions()));
    }

    public record DashboardResponse(UUID schoolId, Instant generatedAt,
                                     MetricGroup activities, AppGroup applications,
                                     ScoreGroup scoreAttempts, AppealGroup scoreAppeals,
                                     FeedbackGroup feedbacks, MediaGroup media,
                                     RankingInfo rankings, PendingActions pendingActions) {}
}
