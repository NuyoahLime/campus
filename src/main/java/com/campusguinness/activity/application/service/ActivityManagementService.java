package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.project.application.port.ProjectRuleVersionPort;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ProjectStatus;

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
    private final ProjectRuleVersionPort ruleVersionPort;
    private final ResponsibleTeacherPort teacherPort;
    private final SchoolMembershipQueryPort membershipPort;

    public ActivityManagementService(ActivityRepository repository,
                                      ActivityProjectPort projectPort,
                                      ChallengeProjectRepository projectRepo,
                                      ProjectRuleVersionPort ruleVersionPort,
                                      ResponsibleTeacherPort teacherPort,
                                      SchoolMembershipQueryPort membershipPort) {
        this.repository = repository;
        this.projectPort = projectPort;
        this.projectRepo = projectRepo;
        this.ruleVersionPort = ruleVersionPort;
        this.teacherPort = teacherPort;
        this.membershipPort = membershipPort;
    }

    // ── Create ──

    public ActivityResult create(CreateActivityCommand cmd) {
        var act = Activity.create(new Activity.Builder()
                .id(new ActivityId(UUID.randomUUID())).schoolId(cmd.schoolId())
                .createdBy(cmd.createdBy()).title(cmd.title()).description(cmd.description())
                .startTime(cmd.startTime()).endTime(cmd.endTime()).location(cmd.location()));
        repository.save(act);
        return new ActivityResult(act.id().value(), act.executionStatus().name(), act.publicStatus().name());
    }

    // ── Update DRAFT ──

    public ActivityResult update(UUID activityId, String title, String description,
                                  java.time.Instant startTime, java.time.Instant endTime,
                                  String location) {
        var act = find(activityId);
        if (title != null) act.updateTitle(title);
        if (description != null) act.updateDescription(description);
        if (startTime != null || endTime != null) act.updateTimeRange(startTime, endTime);
        if (location != null) act.updateLocation(location);
        repository.save(act);
        return new ActivityResult(act.id().value(), act.executionStatus().name(), act.publicStatus().name());
    }

    // ── Execution Lifecycle ──

    public ActivityResult publish(UUID id) {
        var act = find(id);
        requirePublishPrerequisites(act);
        act.publish();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    private void requirePublishPrerequisites(Activity act) {
        if (act.title() == null || act.title().isBlank())
            throw new IllegalStateException("Cannot publish: title is required");
        if (act.startTime() == null)
            throw new IllegalStateException("Cannot publish: startTime is required");
        if (act.endTime() == null)
            throw new IllegalStateException("Cannot publish: endTime is required");
        if (act.startTime() != null && act.endTime() != null && act.endTime().isBefore(act.startTime()))
            throw new IllegalStateException("Cannot publish: endTime must not be before startTime");
        if (act.location() == null || act.location().isBlank())
            throw new IllegalStateException("Cannot publish: location is required");
        var projects = projectPort.findByActivity(act.id().value());
        if (projects.isEmpty())
            throw new IllegalStateException("Cannot publish: at least one project required");

        // Every project must have at least one assignable teacher
        var projectIds = projects.stream().map(p -> p.id()).toList();
        var counts = teacherPort.countAssignableByActivityProjects(projectIds);
        for (var p : projects) {
            long c = counts.getOrDefault(p.id(), 0L);
            if (c == 0) {
                throw new IllegalStateException("Cannot publish: every project must have at least one responsible teacher");
            }
        }
    }

    public ActivityResult beginExecution(UUID id) {
        var act = find(id);
        act.beginExecution();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

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

    // ── Public Review (School) ──

    public ActivityResult submitForPublicReview(UUID id) {
        var act = find(id);
        act.submitForReview();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    public ActivityResult withdrawPublic(UUID id) {
        var act = find(id);
        act.schoolWithdraw();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    // ── Public Review (Platform Admin) ──

    public ActivityResult platformApprove(UUID id) {
        var act = find(id);
        act.platformApprove();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    public ActivityResult platformReject(UUID id, String reason) {
        var act = find(id);
        act.platformReject(reason);
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    public ActivityResult makePublic(UUID id) {
        var act = find(id);
        act.makePublic();
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    public ActivityResult platformTakedown(UUID id, String reason) {
        var act = find(id);
        act.platformTakedown(reason);
        repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    // ── Project Configuration ──

    @Transactional(readOnly = true)
    public List<ActivityProjectPort.ProjectRecord> listProjects(UUID activityId) {
        find(activityId);
        return projectPort.findByActivity(activityId);
    }

    public ActivityProjectPort.ProjectRecord addProject(UUID activityId, UUID projectId) {
        var activity = find(activityId);
        requireDraftForProjectConfiguration(activity);

        if (projectPort.existsByActivityAndProject(activityId, projectId)) {
            throw new IllegalArgumentException("Project already added to this activity");
        }

        var project = projectRepo.findById(new ChallengeProjectId(projectId))
                .orElseThrow(() -> new IllegalArgumentException("ChallengeProject not found: " + projectId));

        if (project.status() != ProjectStatus.PUBLISHED) {
            throw new IllegalArgumentException("Project must be PUBLISHED to be added to an activity");
        }

        UUID ruleVersionId = ruleVersionPort.findCurrentRuleVersionId(projectId)
                .orElseThrow(() -> new IllegalStateException("Project has no current rule version"));

        return projectPort.add(activityId, projectId, ruleVersionId);
    }

    public void removeProject(UUID activityId, UUID projectId) {
        var activity = find(activityId);
        requireDraftForProjectConfiguration(activity);

        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found on this activity"));

        // Delete teacher assignments first (FK RESTRICT), then the project
        teacherPort.deleteAllByActivityProject(ap.id());
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

        UUID membershipId = membershipPort.findActiveTeacherMembershipId(teacherId, activity.schoolId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Teacher must be an active TEACHER at this school with NORMAL account"));

        if (teacherPort.exists(ap.id(), membershipId)) {
            throw new IllegalStateException("Teacher already assigned to this project");
        }

        return teacherPort.assign(ap.id(), membershipId, teacherId);
    }

    public void unassignResponsibleTeacher(UUID activityId, UUID projectId, UUID teacherId) {
        var activity = find(activityId);
        requireNotTerminal(activity);
        var ap = projectPort.findByActivityAndProject(activityId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not configured on this activity"));

        var assignment = teacherPort.findByActivityProjectAndUserId(ap.id(), teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not assigned to this project"));

        // For PUBLISHED/IN_PROGRESS: cannot remove the last assignable teacher
        if (activity.executionStatus() == ExecutionStatus.PUBLISHED
                || activity.executionStatus() == ExecutionStatus.IN_PROGRESS) {
            var counts = teacherPort.countAssignableByActivityProjects(List.of(ap.id()));
            long currentAssignable = counts.getOrDefault(ap.id(), 0L);
            if (currentAssignable <= 1 && "ACTIVE".equals(assignment.membershipStatus())
                    && "NORMAL".equals(assignment.accountStatus())) {
                throw new IllegalStateException("Cannot remove the last assignable teacher from a published project");
            }
        }

        teacherPort.unassignById(assignment.id());
    }

    // ── Queries ──

    @Transactional(readOnly = true)
    public Activity findById(UUID id) { return find(id); }

    // ── Internal ──

    private Activity find(UUID id) {
        return repository.findById(new ActivityId(id))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }

    private void requireNotTerminal(Activity activity) {
        var s = activity.executionStatus();
        if (s == ExecutionStatus.ENDED || s == ExecutionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify " + s + " activity");
        }
    }

    private void requireDraftForProjectConfiguration(Activity activity) {
        if (activity.executionStatus() != ExecutionStatus.DRAFT) {
            throw new IllegalStateException(
                    "Activity project configuration only allowed in DRAFT, current: " + activity.executionStatus());
        }
    }
}
