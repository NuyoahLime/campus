package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ActivityApplicationService {
    private final ActivityApplicationRepository repository;
    private final CurrentActor currentActor;

    public ActivityApplicationService(ActivityApplicationRepository r, CurrentActor currentActor) {
        this.repository = r;
        this.currentActor = currentActor;
    }

    public ActivityApplicationResult submit(SubmitActivityApplicationCommand cmd) {
        UUID actorUserId = currentActor.requireUserId();
        var app = ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(cmd.schoolId())
                .applicantId(actorUserId).title(cmd.title()).description(cmd.description()));
        app.submit();
        repository.save(app);
        return new ActivityApplicationResult(app.id().value(), app.status().name(), null);
    }

    public ActivityApplicationResult approve(UUID id, UUID activityId) {
        UUID actorUserId = currentActor.requireUserId();
        var app = find(id); app.approve(actorUserId, activityId); repository.save(app);
        return new ActivityApplicationResult(id, app.status().name(), activityId);
    }

    public ActivityApplicationResult reject(UUID id, String reason) {
        UUID actorUserId = currentActor.requireUserId();
        var app = find(id); app.reject(actorUserId, reason); repository.save(app);
        return new ActivityApplicationResult(id, app.status().name(), null);
    }

    public ActivityApplicationResult withdraw(UUID id) {
        var app = find(id); app.withdraw(); repository.save(app);
        return new ActivityApplicationResult(id, app.status().name(), null);
    }

    private ActivityApplication find(UUID id) {
        return repository.findById(new ActivityApplicationId(id))
                .orElseThrow(() -> new IllegalArgumentException("ActivityApplication not found: " + id));
    }
}
