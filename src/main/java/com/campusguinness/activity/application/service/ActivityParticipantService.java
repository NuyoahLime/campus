package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.port.ActivityParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.query.model.ParticipantListResult;
import com.campusguinness.activity.application.query.model.ProjectParticipantListResult;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.identity.internal.domain.UserId;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreValue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ActivityParticipantService {
    private final ActivityRepository activityRepo;
    private final ActivityProjectPort projectPort;
    private final ActivityParticipantPort participantPort;
    private final ActivityProjectParticipantPort projectParticipantPort;
    private final ActivityParticipantQueryPort participantQueryPort;
    private final SchoolMembershipQueryPort membershipPort;
    private final ScoreAttemptRepository scoreAttemptRepo;
    private final UserRepository userRepo;

    public ActivityParticipantService(ActivityRepository activityRepo,
                                       ActivityProjectPort projectPort,
                                       ActivityParticipantPort participantPort,
                                       ActivityProjectParticipantPort projectParticipantPort,
                                       ActivityParticipantQueryPort participantQueryPort,
                                       SchoolMembershipQueryPort membershipPort,
                                       ScoreAttemptRepository scoreAttemptRepo,
                                       UserRepository userRepo) {
        this.activityRepo = activityRepo;
        this.projectPort = projectPort;
        this.participantPort = participantPort;
        this.projectParticipantPort = projectParticipantPort;
        this.participantQueryPort = participantQueryPort;
        this.membershipPort = membershipPort;
        this.scoreAttemptRepo = scoreAttemptRepo;
        this.userRepo = userRepo;
    }

    // ── Roster Management ──

    public ActivityParticipantPort.ParticipantRecord addParticipant(UUID activityId, UUID studentId) {
        var activity = findActivity(activityId);
        requireNotTerminal(activity);

        UUID membershipId = requireActiveStudentMembership(studentId, activity.schoolId());

        if (participantPort.existsByActivityAndMembership(activityId, membershipId)) {
            throw new IllegalStateException("Student already in this activity");
        }

        return participantPort.add(activityId, membershipId);
    }

    public void removeParticipant(UUID activityId, UUID studentId) {
        var activity = findActivity(activityId);
        requireNotTerminal(activity);

        UUID membershipId = requireActiveStudentMembership(studentId, activity.schoolId());
        var participant = participantPort.findByActivityAndMembership(activityId, membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Student not in this activity"));

        // Check no project assignments
        if (projectParticipantPort.existsByParticipantId(participant.id())) {
            throw new IllegalStateException("Cannot remove participant with project assignments");
        }

        // Check no score attempts in this activity
        var projectRecords = projectPort.findByActivity(activityId);
        for (var ap : projectRecords) {
            if (scoreAttemptRepo.existsByActivityProjectIdAndStudentId(ap.id(), studentId)) {
                throw new IllegalStateException("Cannot remove participant with existing score attempts");
            }
        }

        participantPort.remove(participant.id());
    }

    @Transactional(readOnly = true)
    public QueryPage<ParticipantListResult> listParticipants(UUID activityId, String keyword, int page, int size) {
        return participantQueryPort.findByActivity(activityId, keyword, page, size);
    }

    // ── Project Assignment ──

    public ActivityProjectParticipantPort.ProjectParticipantRecord assignToProject(
            UUID activityId, UUID projectId, UUID studentId, UUID assignedBy) {
        var activity = findActivity(activityId);
        requireNotTerminal(activity);

        // Verify project is configured in this activity
        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not configured on this activity"));

        // Verify student is in the activity roster
        UUID membershipId = requireActiveStudentMembership(studentId, activity.schoolId());
        var participant = participantPort.findByActivityAndMembership(activityId, membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Student not in this activity"));

        if (projectParticipantPort.existsByProjectAndParticipant(ap.id(), participant.id())) {
            throw new IllegalStateException("Student already assigned to this project");
        }

        return projectParticipantPort.assign(ap.id(), participant.id(), assignedBy);
    }

    public void unassignFromProject(UUID activityId, UUID projectId, UUID studentId) {
        var activity = findActivity(activityId);
        requireNotTerminal(activity);

        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not configured on this activity"));

        UUID membershipId = requireActiveStudentMembership(studentId, activity.schoolId());
        var participant = participantPort.findByActivityAndMembership(activityId, membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Student not in this activity"));

        if (!projectParticipantPort.existsByProjectAndParticipant(ap.id(), participant.id())) {
            throw new IllegalArgumentException("Student not assigned to this project");
        }

        // Check for existing score attempts before unassigning
        if (scoreAttemptRepo.existsByActivityProjectIdAndStudentId(ap.id(), studentId)) {
            throw new IllegalStateException("Cannot unassign participant with existing score attempts");
        }

        projectParticipantPort.unassign(ap.id(), participant.id());
    }

    @Transactional(readOnly = true)
    public List<ProjectParticipantListResult> listProjectParticipants(UUID activityId, UUID projectId) {
        var activity = findActivity(activityId);
        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not configured on this activity"));

        var assignments = projectParticipantPort.findByProject(ap.id());
        if (assignments.isEmpty()) return List.of();

        // Collect participant IDs and their membership IDs
        var participantIds = assignments.stream()
                .map(ActivityProjectParticipantPort.ProjectParticipantRecord::activityParticipantId)
                .toList();
        var participantMap = participantIds.stream()
                .map(id -> participantPort.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ActivityParticipantPort.ParticipantRecord::id, p -> p));

        // Batch: membershipId → userId
        var membershipIds = participantMap.values().stream()
                .map(ActivityParticipantPort.ParticipantRecord::studentMembershipId)
                .distinct().toList();
        var membershipToUserId = membershipPort.findUserIdsByMembershipIds(membershipIds);

        // Collect studentIds for batch queries
        var studentIds = participantMap.values().stream()
                .map(p -> membershipToUserId.get(p.studentMembershipId()))
                .filter(Objects::nonNull)
                .distinct().toList();

        // Batch: userId → username (displayName)
        var userIds = studentIds.stream().map(UserId::new).toList();
        var userMap = userRepo.findByIds(userIds).stream()
                .collect(Collectors.toMap(u -> u.id().value(), u -> u.username()));

        // Batch: all score attempts for this project across all students
        var scoreAttempts = scoreAttemptRepo.findByActivityProjectIdAndStudentIds(ap.id(), studentIds);
        // Sort by submittedAt descending (newest first), nulls last
        var sorted = new ArrayList<>(scoreAttempts);
        sorted.sort((a, b) -> {
            var ta = a.submittedAt();
            var tb = b.submittedAt();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        var scoresByStudent = sorted.stream()
                .collect(Collectors.groupingBy(
                        com.campusguinness.score.internal.domain.ScoreAttempt::studentId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return assignments.stream().map(a -> {
            var p = participantMap.get(a.activityParticipantId());
            UUID studentId = p != null ? membershipToUserId.get(p.studentMembershipId()) : null;
            String displayName = studentId != null ? userMap.get(studentId) : null;

            var studentScores = scoresByStudent.getOrDefault(studentId, List.of());
            int attemptCount = studentScores.size();
            boolean hasScoreAttempt = attemptCount > 0;
            boolean hasApproved = studentScores.stream()
                    .anyMatch(s -> s.status() == com.campusguinness.score.internal.domain.AttemptStatus.APPROVED);
            var latest = hasScoreAttempt ? studentScores.getFirst() : null;
            String latestStatus = latest != null ? latest.status().name() : null;
            String latestScoreValue = latest != null ? formatScoreValue(latest) : null;
            UUID latestAttemptId = latest != null ? latest.id().value() : null;

            return new ProjectParticipantListResult(a.id(), a.activityProjectId(),
                    a.activityParticipantId(), studentId, displayName,
                    attemptCount, hasScoreAttempt, latestAttemptId,
                    latestStatus, latestScoreValue, hasApproved, a.assignedAt());
        }).toList();
    }

    // ── Helpers ──

    public Activity findActivity(UUID id) {
        return activityRepo.findById(new ActivityId(id))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }

    private void requireNotTerminal(Activity activity) {
        var s = activity.executionStatus();
        if (s == ExecutionStatus.ENDED || s == ExecutionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify " + s + " activity");
        }
    }

    private UUID requireActiveStudentMembership(UUID studentId, UUID schoolId) {
        return membershipPort.findActiveStudentMembershipId(studentId, schoolId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Student not found or not an active STUDENT at school " + schoolId));
    }

    private static String formatScoreValue(ScoreAttempt s) {
        ScoreValue v = s.scoreValue();
        return switch (v) {
            case ScoreValue.IntegerScore is -> String.valueOf(is.value());
            case ScoreValue.DecimalScore ds -> ds.value().toPlainString();
            case ScoreValue.DurationScore ds -> ds.durationMs() + "ms";
            case ScoreValue.GradeScore gs -> gs.grade();
        };
    }
}
