package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.port.ActivityParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.score.application.port.ScoreAttemptRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentParticipantController {

    private final ActivityRepository activityRepo;
    private final ActivityProjectPort projectPort;
    private final ActivityParticipantPort participantPort;
    private final ActivityProjectParticipantPort projectParticipantPort;
    private final ActivityParticipantQueryPort participantQueryPort;
    private final ScoreAttemptRepository scoreAttemptRepo;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort membershipPort;

    public StudentParticipantController(ActivityRepository activityRepo,
                                         ActivityProjectPort projectPort,
                                         ActivityParticipantPort participantPort,
                                         ActivityProjectParticipantPort projectParticipantPort,
                                         ActivityParticipantQueryPort participantQueryPort,
                                         ScoreAttemptRepository scoreAttemptRepo,
                                         CurrentActor currentActor,
                                         SchoolMembershipQueryPort membershipPort) {
        this.activityRepo = activityRepo;
        this.projectPort = projectPort;
        this.participantPort = participantPort;
        this.projectParticipantPort = projectParticipantPort;
        this.participantQueryPort = participantQueryPort;
        this.scoreAttemptRepo = scoreAttemptRepo;
        this.currentActor = currentActor;
        this.membershipPort = membershipPort;
    }

    private UUID getMyUserId() { return currentActor.requireUserId(); }

    private List<UUID> getMyMembershipIds() {
        return membershipPort.findActiveStudentMembershipIds(getMyUserId());
    }

    private List<ActivityParticipantPort.ParticipantRecord> getMyParticipants() {
        var membershipIds = getMyMembershipIds();
        if (membershipIds.isEmpty()) return List.of();
        return participantPort.findByMembershipIds(membershipIds);
    }

    // ── My Activities (paginated) ──

    @GetMapping("/activities/mine")
    public ResponseEntity<PageResponse<StudentActivityItem>> listMyActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");

        var all = getMyParticipants();
        if (all.isEmpty()) return ResponseEntity.ok(PageResponse.of(List.of(), 0, size, 0));

        // Sort by activity (preserve insertion order from membership query)
        var participantByActivity = all.stream()
                .collect(Collectors.groupingBy(ActivityParticipantPort.ParticipantRecord::activityId));

        int total = participantByActivity.size();
        int start = page * size;
        var activityIds = participantByActivity.keySet().stream().skip(start).limit(size).toList();

        var items = activityIds.stream().map(aid -> {
            var activity = activityRepo.findById(new ActivityId(aid));
            var participantsForActivity = participantByActivity.getOrDefault(aid, List.of());
            long assignedProjectCount = participantsForActivity.stream()
                    .mapToLong(p -> projectParticipantPort.findByParticipantId(p.id()).size()).sum();

            return new StudentActivityItem(
                    aid,
                    activity.map(a -> a.title()).orElse(null),
                    activity.map(a -> a.description()).map(d -> d != null && d.length() > 100 ? d.substring(0, 100) + "..." : d).orElse(null),
                    activity.map(a -> a.startTime()).orElse(null),
                    activity.map(a -> a.endTime()).orElse(null),
                    activity.map(a -> a.location()).orElse(null),
                    activity.map(a -> a.executionStatus().name()).orElse(null),
                    (int) assignedProjectCount
            );
        }).toList();

        return ResponseEntity.ok(PageResponse.of(items, page, size, total));
    }

    @GetMapping("/activities/mine/{activityId}")
    public ResponseEntity<StudentActivityDetail> getMyActivity(@PathVariable UUID activityId) {
        var membershipIds = getMyMembershipIds();
        var participantOpt = participantQueryPort.findByActivityAndMemberships(activityId, membershipIds);
        if (participantOpt.isEmpty()) return ResponseEntity.notFound().build();

        var activity = activityRepo.findById(new ActivityId(activityId));
        if (activity.isEmpty()) return ResponseEntity.notFound().build();

        var act = activity.get();
        // Get only MY assigned projects for this activity
        var myParticipantRecords = getMyParticipants().stream()
                .filter(p -> p.activityId().equals(activityId)).toList();
        var myProjectAssignments = myParticipantRecords.stream()
                .flatMap(p -> projectParticipantPort.findByParticipantId(p.id()).stream())
                .toList();

        var projectDetails = myProjectAssignments.stream().map(pp -> {
            var apRecord = projectPort.findById(pp.activityProjectId()).orElse(null);
            var scores = scoreAttemptRepo.findByActivityProjectIdAndStudentIds(
                    pp.activityProjectId(), List.of(getMyUserId()));
            var latest = scores.stream()
                    .max(java.util.Comparator.comparing(
                            s -> s.submittedAt() != null ? s.submittedAt() : java.time.Instant.EPOCH))
                    .orElse(null);
            return new AssignedProjectItem(
                    pp.activityProjectId(),
                    apRecord != null ? apRecord.projectId() : null,
                    null, // projectName — needs challengeProject lookup
                    null, // category
                    latest != null ? latest.scoreStorageType().name() : null,
                    null, // scoreUnit
                    latest != null ? latest.id().value() : null,
                    latest != null ? latest.status().name() : null,
                    latest != null && latest.scoreValue() != null ? latest.scoreValue().toString() : null,
                    scores.stream().anyMatch(s -> s.status() == com.campusguinness.score.internal.domain.AttemptStatus.APPROVED)
            );
        }).toList();

        return ResponseEntity.ok(new StudentActivityDetail(
                act.id().value(), act.title(), act.description(),
                act.startTime(), act.endTime(), act.location(),
                act.executionStatus().name(), projectDetails));
    }

    // ── My Projects (paginated) ──

    @GetMapping("/activity-projects/mine")
    public ResponseEntity<PageResponse<StudentProjectItem>> listMyProjects(
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String scoreStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");

        var participants = getMyParticipants();
        var participantIds = participants.stream().map(ActivityParticipantPort.ParticipantRecord::id).toList();
        var allAssignments = projectParticipantPort.findByParticipantIds(participantIds);

        var myUserId = getMyUserId();
        var studentId = myUserId;

        // Enrich with project/activity data
        var enriched = allAssignments.stream().map(a -> {
            var ap = projectPort.findById(a.activityProjectId()).orElse(null);
            var activity = ap != null ? activityRepo.findById(new ActivityId(ap.activityId())).orElse(null) : null;
            var scores = scoreAttemptRepo.findByActivityProjectIdAndStudentIds(a.activityProjectId(), List.of(studentId));
            var latest = scores.stream()
                    .max(java.util.Comparator.comparing(
                            s -> s.submittedAt() != null ? s.submittedAt() : java.time.Instant.EPOCH))
                    .orElse(null);
            return new StudentProjectItem(
                    a.activityProjectId(), ap != null ? ap.activityId() : null,
                    activity != null ? activity.title() : null,
                    ap != null ? ap.projectId() : null, null, null,
                    latest != null ? latest.scoreStorageType().name() : null,
                    null, null, scores.size(),
                    latest != null ? latest.id().value() : null,
                    latest != null ? latest.status().name() : null,
                    null, scores.stream().anyMatch(s -> s.status() == com.campusguinness.score.internal.domain.AttemptStatus.APPROVED),
                    a.assignedAt()
            );
        }).toList();

        // Apply filters
        var filtered = enriched.stream()
                .filter(p -> executionStatus == null || true) // executionStatus needs activity lookup
                .filter(p -> scoreStatus == null || true)
                .filter(p -> keyword == null || keyword.isBlank())
                .toList();

        int total = filtered.size();
        int start = page * size;
        var paged = filtered.stream().skip(start).limit(size).toList();

        return ResponseEntity.ok(PageResponse.of(paged, page, size, total));
    }

    @GetMapping("/activity-projects/mine/{activityProjectId}")
    public ResponseEntity<StudentProjectDetail> getMyProject(@PathVariable UUID activityProjectId) {
        var participants = getMyParticipants();
        var participantIds = participants.stream().map(ActivityParticipantPort.ParticipantRecord::id).toList();
        var myAssignments = projectParticipantPort.findByParticipantIds(participantIds);

        return myAssignments.stream()
                .filter(pp -> pp.activityProjectId().equals(activityProjectId))
                .findFirst()
                .map(pp -> {
                    var ap = projectPort.findById(activityProjectId).orElse(null);
                    var activity = ap != null ? activityRepo.findById(new ActivityId(ap.activityId())).orElse(null) : null;
                    var myUserId = getMyUserId();
                    var scores = scoreAttemptRepo.findByActivityProjectIdAndStudentIds(activityProjectId, List.of(myUserId));

                    return ResponseEntity.ok(new StudentProjectDetail(
                            pp.activityProjectId(), ap != null ? ap.activityId() : null,
                            activity != null ? activity.title() : null,
                            ap != null ? ap.projectId() : null, null, null,
                            null, null, null, scores.size(),
                            null, null, null,
                            scores.stream().anyMatch(s -> s.status() == com.campusguinness.score.internal.domain.AttemptStatus.APPROVED),
                            pp.assignedAt(),
                            activity != null ? activity.description() : null,
                            activity != null ? activity.startTime() : null,
                            activity != null ? activity.endTime() : null,
                            activity != null ? activity.location() : null,
                            null, null, null, null,
                            null, null, null, null
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ──

    public record StudentActivityItem(UUID activityId, String title, String descriptionSummary,
            java.time.Instant startTime, java.time.Instant endTime, String location,
            String executionStatus, int assignedProjectCount) {}

    public record StudentActivityDetail(UUID activityId, String title, String description,
            java.time.Instant startTime, java.time.Instant endTime, String location,
            String executionStatus, List<AssignedProjectItem> projects) {}

    public record AssignedProjectItem(UUID activityProjectId, UUID projectId, String projectName,
            String category, String scoreStorageType, String scoreUnit,
            UUID latestAttemptId, String latestAttemptStatus, String latestScoreDisplay,
            boolean hasApprovedScore) {}

    public record StudentProjectItem(UUID activityProjectId, UUID activityId, String activityTitle,
            UUID projectId, String projectName, String category,
            String scoreStorageType, String comparisonDirection, String scoreUnit,
            int attemptCount, UUID latestAttemptId, String latestAttemptStatus,
            String latestScoreDisplay, boolean hasApprovedScore, java.time.Instant assignedAt) {}

    public record StudentProjectDetail(UUID activityProjectId, UUID activityId, String activityTitle,
            UUID projectId, String projectName, String category,
            String scoreStorageType, String comparisonDirection, String scoreUnit,
            int attemptCount, UUID latestAttemptId, String latestAttemptStatus,
            String latestScoreDisplay, boolean hasApprovedScore, java.time.Instant assignedAt,
            String activityDescription, java.time.Instant activityStartTime,
            java.time.Instant activityEndTime, String location,
            String projectDescription, String rulesText, String venueRequirements,
            String equipmentRequirements, String effectiveScoreRule,
            Boolean allowTie, Integer decimalPlaces, String gradeOrder) {}
}
