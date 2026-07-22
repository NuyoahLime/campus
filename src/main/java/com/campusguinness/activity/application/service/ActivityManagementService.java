package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ProjectStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ActivityManagementService {
    private final ActivityRepository repository;
    private final ActivityProjectPort projectPort;
    private final ChallengeProjectRepository projectRepo;
    private final ResponsibleTeacherPort teacherPort;
    private final JdbcTemplate jdbc;

    public ActivityManagementService(ActivityRepository repository,
                                      ActivityProjectPort projectPort,
                                      ChallengeProjectRepository projectRepo,
                                      ResponsibleTeacherPort teacherPort,
                                      JdbcTemplate jdbc) {
        this.repository = repository;
        this.projectPort = projectPort;
        this.projectRepo = projectRepo;
        this.teacherPort = teacherPort;
        this.jdbc = jdbc;
    }

    public ActivityResult create(CreateActivityCommand cmd) {
        var act = Activity.create(new Activity.Builder()
                .id(new ActivityId(UUID.randomUUID())).schoolId(cmd.schoolId())
                .createdBy(cmd.createdBy()).title(cmd.title()).description(cmd.description())
                .startTime(cmd.startTime()).endTime(cmd.endTime()).location(cmd.location()));
        repository.save(act);
        return new ActivityResult(act.id().value(), act.executionStatus().name(), act.publicStatus().name());
    }

    public ActivityResult publish(UUID id) {
        var act = find(id); act.publish(); repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    private Activity find(UUID id) {
        return repository.findById(new ActivityId(id))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }

    // ── Project Configuration ──

    @Transactional(readOnly = true)
    public List<ActivityProjectPort.ProjectRecord> listProjects(UUID activityId) {
        find(activityId); // validates activity exists
        return projectPort.findByActivity(activityId);
    }

    public ActivityProjectPort.ProjectRecord addProject(UUID activityId, UUID projectId) {
        var activity = find(activityId);
        requireNotTerminal(activity);

        if (projectPort.existsByActivityAndProject(activityId, projectId)) {
            throw new IllegalArgumentException("Project already added to this activity");
        }

        var project = projectRepo.findById(new ChallengeProjectId(projectId))
                .orElseThrow(() -> new IllegalArgumentException("ChallengeProject not found: " + projectId));

        if (project.status() != ProjectStatus.PUBLISHED) {
            throw new IllegalArgumentException("Project must be PUBLISHED to be added to an activity");
        }

        return projectPort.add(activityId, projectId, UUID.randomUUID());
    }

    public void removeProject(UUID activityId, UUID projectId) {
        var activity = find(activityId);
        requireNotTerminal(activity);

        if (!projectPort.existsByActivityAndProject(activityId, projectId)) {
            throw new IllegalArgumentException("Project not found on this activity");
        }
        projectPort.remove(activityId, projectId);
    }

    // ── Responsible Teacher Assignment ──

    @Transactional(readOnly = true)
    public List<ResponsibleTeacherPort.TeacherRecord> listResponsibleTeachers(UUID activityId, UUID projectId) {
        find(activityId);
        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not configured on this activity"));
        return teacherPort.findByActivityProject(ap.id());
    }

    public ResponsibleTeacherPort.TeacherRecord assignResponsibleTeacher(UUID activityId, UUID projectId, UUID teacherId) {
        var activity = find(activityId);
        requireNotTerminal(activity);
        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not configured on this activity"));

        var membership = resolveTeacherMembership(activityId, teacherId);

        UUID membershipId = (UUID) membership.get("id");
        if (teacherPort.exists(ap.id(), membershipId)) {
            throw new IllegalArgumentException("Teacher already assigned to this project");
        }

        return teacherPort.assign(ap.id(), membershipId, teacherId);
    }

    public void unassignResponsibleTeacher(UUID activityId, UUID projectId, UUID teacherId) {
        var activity = find(activityId);
        requireNotTerminal(activity);
        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not configured on this activity"));

        var membership = resolveTeacherMembership(activityId, teacherId);

        UUID membershipId = (UUID) membership.get("id");
        if (!teacherPort.exists(ap.id(), membershipId)) {
            throw new IllegalArgumentException("Teacher not assigned to this project");
        }
        teacherPort.unassign(ap.id(), membershipId);
    }

    private void requireNotTerminal(Activity activity) {
        var s = activity.executionStatus();
        if (s == ExecutionStatus.ENDED || s == ExecutionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify " + s + " activity");
        }
    }

    private java.util.Map<String, Object> resolveTeacherMembership(UUID activityId, UUID teacherId) {
        var activity = find(activityId);
        var rows = jdbc.queryForList(
                "SELECT id, role_in_school, status FROM school_memberships " +
                        "WHERE user_id = ? AND school_id = ? AND role_in_school = 'TEACHER' AND status = 'ACTIVE'",
                teacherId, activity.schoolId());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Teacher not found or not an active TEACHER at school " + activity.schoolId());
        }
        return rows.getFirst();
    }

    // ── Lifecycle ──

    public ActivityResult finish(UUID id) {
        var act = find(id);
        act.end();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    public ActivityResult cancel(UUID id) {
        var act = find(id);
        act.cancel();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }
}
