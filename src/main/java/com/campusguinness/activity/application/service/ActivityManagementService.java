package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.internal.domain.*;
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

    public ActivityManagementService(ActivityRepository repository,
                                      ActivityProjectPort projectPort,
                                      ChallengeProjectRepository projectRepo) {
        this.repository = repository;
        this.projectPort = projectPort;
        this.projectRepo = projectRepo;
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
        find(activityId); // validates activity exists

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
        find(activityId); // validates activity exists

        if (!projectPort.existsByActivityAndProject(activityId, projectId)) {
            throw new IllegalArgumentException("Project not found on this activity");
        }
        projectPort.remove(activityId, projectId);
    }
}
