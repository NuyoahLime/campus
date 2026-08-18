package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.command.UpdateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectRepository;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionRepository;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ProjectStatus;
import com.campusguinness.school.application.query.SchoolOperationalQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ActivityManagementService {
    private final ActivityRepository repository;
    private final SchoolResourceAuthorization authorization;
    private final ActivityProjectRepository activityProjects;
    private final ChallengeProjectRepository projects;
    private final ProjectRuleVersionRepository ruleVersions;
    private final SchoolOperationalQuery schoolOperational;

    @Autowired
    public ActivityManagementService(ActivityRepository repository,
            SchoolResourceAuthorization authorization,
            ActivityProjectRepository activityProjects,
            ChallengeProjectRepository projects,
            ProjectRuleVersionRepository ruleVersions,
            SchoolOperationalQuery schoolOperational) {
        this.repository = repository;
        this.authorization = authorization;
        this.activityProjects = activityProjects;
        this.projects = projects;
        this.ruleVersions = ruleVersions;
        this.schoolOperational = schoolOperational;
    }

    /** Compatibility constructor for pre-Stage-19 domain tests. */
    public ActivityManagementService(ActivityRepository repository, SchoolResourceAuthorization authorization) {
        this.repository = repository;
        this.authorization = authorization;
        this.activityProjects = null;
        this.projects = null;
        this.ruleVersions = null;
        this.schoolOperational = null;
    }

    public ActivityResult create(CreateActivityCommand cmd) {
        UUID schoolId;
        UUID actorUserId;
        UUID ruleVersionId = null;
        if (projects == null) {
            schoolId = cmd.projectId();
            actorUserId = authorization.requireSchoolAdmin(schoolId);
        } else {
            schoolId = authorization.requireUniqueSchoolAdminSchool();
            actorUserId = authorization.requireSchoolAdmin(schoolId);
            requireNormalSchool(schoolId);
            var project = projects.findById(new ChallengeProjectId(cmd.projectId()))
                    .orElseThrow(() -> unavailable("ChallengeProject not found: " + cmd.projectId()));
            if (project.status() != ProjectStatus.PUBLISHED || project.currentRuleVersionId() == null
                    || ruleVersions.findAllByProjectId(cmd.projectId()).stream()
                    .noneMatch(version -> version.id().equals(project.currentRuleVersionId()))) {
                throw unavailable("ChallengeProject is not available for new activities.");
            }
            ruleVersionId = project.currentRuleVersionId();
        }
        var activity = Activity.create(new Activity.Builder()
                .id(new ActivityId(UUID.randomUUID())).schoolId(schoolId)
                .createdBy(actorUserId).title(cmd.title()).description(cmd.description())
                .startTime(cmd.startTime()).endTime(cmd.endTime()).location(cmd.location()));
        repository.save(activity);
        if (activityProjects != null) {
            activityProjects.save(new ActivityProjectRepository.ActivityProjectSnapshot(
                    UUID.randomUUID(), activity.id().value(), cmd.projectId(), ruleVersionId));
        }
        return result(activity);
    }

    public ActivityResult update(UUID id, UpdateActivityCommand cmd) {
        var activity = find(id);
        authorization.requireSchoolAdmin(activity.schoolId());
        requireNormalSchool(activity.schoolId());
        activity.updateTitle(cmd.title());
        activity.updateDescription(cmd.description());
        activity.updateTimeRange(cmd.startTime(), cmd.endTime());
        activity.updateLocation(cmd.location());
        repository.save(activity);
        return result(activity);
    }

    public ActivityResult publish(UUID id) {
        var activity = find(id);
        authorization.requireSchoolAdmin(activity.schoolId());
        requireNormalSchool(activity.schoolId());
        activity.publish();
        repository.save(activity);
        return result(activity);
    }

    public ActivityResult cancel(UUID id) {
        var activity = find(id);
        authorization.requireSchoolAdmin(activity.schoolId());
        requireNormalSchool(activity.schoolId());
        activity.cancel();
        repository.save(activity);
        return result(activity);
    }

    private Activity find(UUID id) {
        return repository.findById(new ActivityId(id))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }

    private void requireNormalSchool(UUID schoolId) {
        if (schoolOperational != null && !schoolOperational.isNormal(schoolId)) {
            throw new IdentityApplicationException("SCHOOL_NOT_OPERATIONAL",
                    "School is not operational for activity management.");
        }
    }

    private IdentityApplicationException unavailable(String message) {
        return new IdentityApplicationException("ACTIVITY_PROJECT_UNAVAILABLE", message);
    }

    private ActivityResult result(Activity activity) {
        return new ActivityResult(activity.id().value(), activity.executionStatus().name(), activity.publicStatus().name());
    }
}
