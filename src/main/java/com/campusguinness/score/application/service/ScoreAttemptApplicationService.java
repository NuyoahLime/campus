package com.campusguinness.score.application.service;

import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.internal.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    public ScoreAttemptApplicationService(ScoreAttemptRepository repository,
                                           ActivityRepository activityRepository,
                                           ActivityProjectPort projectPort,
                                           ResponsibleTeacherPort teacherPort,
                                           JdbcTemplate jdbc) {
        this.repository = repository;
        this.activityRepository = activityRepository;
        this.projectPort = projectPort;
        this.teacherPort = teacherPort;
        this.jdbc = jdbc;
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
        var execStatus = activity.executionStatus();
        if (execStatus == ExecutionStatus.ENDED || execStatus == ExecutionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot submit score for " + execStatus + " activity");
        }

        // Verify actor has ACTIVE TEACHER membership at this school
        var rows = jdbc.queryForList(
                "SELECT id FROM school_memberships WHERE user_id = ? AND school_id = ? AND role_in_school = 'TEACHER' AND status = 'ACTIVE'",
                UUID.class, cmd.enteredBy(), realSchoolId);
        if (rows.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Actor " + cmd.enteredBy() + " is not an ACTIVE TEACHER at school " + realSchoolId);
        }
        UUID membershipId = rows.getFirst();

        // Verify responsible teacher assignment
        if (!teacherPort.exists(apRecord.id(), membershipId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Actor is not assigned as responsible teacher for this activity project");
        }

        // Verify student is assigned to this activity project
        var assigned = jdbc.queryForList(
                "SELECT 1 FROM activity_project_participants app " +
                "JOIN activity_applications aa ON app.activity_application_id = aa.id " +
                "WHERE app.activity_project_id = ? AND aa.applicant_id = ? AND aa.application_status = 'APPROVED'",
                cmd.activityProjectId(), cmd.studentId());
        if (assigned.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException(
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
        return repository.findById(new ScoreAttemptId(attemptId))
                .filter(s -> s.studentId().equals(studentId))
                .map(s -> new ScoreAttemptResult(s.id().value(), s.status().name(), s.scoreStorageType().name()))
                .orElseThrow(() -> new IllegalArgumentException("ScoreAttempt not found: " + attemptId));
    }
}
