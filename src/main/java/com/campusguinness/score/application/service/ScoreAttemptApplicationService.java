package com.campusguinness.score.application.service;

import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.internal.domain.*;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ScoreAttemptApplicationService {
    private final ScoreAttemptRepository repository;
    private final ActivityRepository activityRepository;
    private final ActivityProjectPort projectPort;
    private final ResponsibleTeacherPort teacherPort;
    private final ActivityProjectParticipantPort projectParticipantPort;
    private final ActivityParticipantQueryPort participantQueryPort;
    private final SchoolMembershipQueryPort membershipPort;

    public ScoreAttemptApplicationService(ScoreAttemptRepository repository,
                                           ActivityRepository activityRepository,
                                           ActivityProjectPort projectPort,
                                           ResponsibleTeacherPort teacherPort,
                                           ActivityProjectParticipantPort projectParticipantPort,
                                           ActivityParticipantQueryPort participantQueryPort,
                                           SchoolMembershipQueryPort membershipPort) {
        this.repository = repository;
        this.activityRepository = activityRepository;
        this.projectPort = projectPort;
        this.teacherPort = teacherPort;
        this.projectParticipantPort = projectParticipantPort;
        this.participantQueryPort = participantQueryPort;
        this.membershipPort = membershipPort;
    }

    public ScoreAttemptResult submit(SubmitScoreCommand cmd) {
        // Load ActivityProject by its PK
        var apRecord = projectPort.findById(cmd.activityProjectId())
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + cmd.activityProjectId()));

        // Load Activity to get real schoolId
        var activity = activityRepository.findById(new ActivityId(apRecord.activityId()))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + apRecord.activityId()));

        UUID realSchoolId = activity.schoolId();

        // Deny submission for terminal activity states
        if (activity.executionStatus() == ExecutionStatus.ENDED
                || activity.executionStatus() == ExecutionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot submit score for " + activity.executionStatus() + " activity");
        }

        // Verify actor has ACTIVE TEACHER membership at this school
        UUID teacherMembershipId = membershipPort.findActiveTeacherMembershipId(
                cmd.enteredBy(), realSchoolId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Actor " + cmd.enteredBy() + " is not an ACTIVE TEACHER at school " + realSchoolId));

        // Verify responsible teacher assignment
        if (!teacherPort.exists(apRecord.id(), teacherMembershipId)) {
            throw new AccessDeniedException(
                    "Actor is not assigned as responsible teacher for this activity project");
        }

        // Verify student is an active STUDENT in this school
        UUID studentMembershipId = membershipPort.findActiveStudentMembershipId(
                cmd.studentId(), realSchoolId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Student " + cmd.studentId() + " is not an active STUDENT at school " + realSchoolId));

        // Verify student is in the activity participant roster
        var participantOpt = participantQueryPort.findByActivityAndMemberships(
                activity.id().value(), List.of(studentMembershipId));
        if (participantOpt.isEmpty()) {
            throw new AccessDeniedException(
                    "Student " + cmd.studentId() + " is not in the activity participant roster");
        }

        // Verify student is assigned to this specific activity project
        if (!projectParticipantPort.existsByProjectAndParticipant(
                apRecord.id(), participantOpt.get().participantId())) {
            throw new AccessDeniedException(
                    "Student " + cmd.studentId() + " is not assigned to this activity project");
        }

        var s = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID())).schoolId(realSchoolId)
                .activityProjectId(cmd.activityProjectId()).studentId(cmd.studentId())
                .attemptNumber(cmd.attemptNumber()).scoreStorageType(cmd.scoreStorageType())
                .scoreValue(cmd.scoreValue()).scoreBusinessTime(cmd.scoreBusinessTime())
                .timeSource(cmd.timeSource()).enteredBy(cmd.enteredBy()));
        s.submit();
        repository.save(s);
        return new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name());
    }

    @Transactional(readOnly = true)
    public List<ScoreAttemptResult> findMyApprovedScores(UUID studentId) {
        return repository.findApprovedByStudentId(studentId).stream()
                .map(s -> new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScoreAttemptResult getMyApprovedScore(UUID attemptId, UUID studentId) {
        return repository.findApprovedByIdAndStudentId(attemptId, studentId)
                .map(s -> new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name()))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAttempt not found: " + attemptId));
    }

    @Transactional(readOnly = true)
    public List<ScoreAttemptResult> listMyProgress(UUID studentId) {
        return repository.findByStudentId(studentId).stream()
                .map(s -> new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScoreAttemptResult getMyProgress(UUID attemptId, UUID studentId) {
        return repository.findByIdAndStudentId(attemptId, studentId)
                .map(s -> new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name()))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAttempt not found: " + attemptId));
    }
}
