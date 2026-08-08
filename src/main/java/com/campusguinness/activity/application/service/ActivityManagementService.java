package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ActivityManagementService {
    private final ActivityRepository repository;
    private final SchoolResourceAuthorization authorization;

    public ActivityManagementService(
            ActivityRepository r,
            SchoolResourceAuthorization authorization) {
        this.repository = r;
        this.authorization = authorization;
    }

    public ActivityResult create(CreateActivityCommand cmd) {
        UUID actorUserId = authorization.requireSchoolAdmin(cmd.schoolId());
        var act = Activity.create(new Activity.Builder()
                .id(new ActivityId(UUID.randomUUID())).schoolId(cmd.schoolId())
                .createdBy(actorUserId).title(cmd.title()).description(cmd.description())
                .startTime(cmd.startTime()).endTime(cmd.endTime()).location(cmd.location()));
        repository.save(act);
        return new ActivityResult(act.id().value(), act.executionStatus().name(), act.publicStatus().name());
    }

    public ActivityResult publish(UUID id) {
        var act = find(id); authorization.requireSchoolAdmin(act.schoolId()); act.publish(); repository.save(act);
        return new ActivityResult(id, act.executionStatus().name(), act.publicStatus().name());
    }

    private Activity find(UUID id) {
        return repository.findById(new ActivityId(id))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + id));
    }
}
