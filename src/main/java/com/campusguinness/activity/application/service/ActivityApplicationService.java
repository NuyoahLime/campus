package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ActivityApplicationService {
    private final ActivityApplicationRepository repository;
    public ActivityApplicationService(ActivityApplicationRepository r) { this.repository = r; }

    public ActivityApplicationResult submit(SubmitActivityApplicationCommand cmd) {
        var app = ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(cmd.schoolId())
                .applicantId(cmd.applicantId()).title(cmd.title()).description(cmd.description()));
        app.submit();
        repository.save(app);
        return new ActivityApplicationResult(app.id().value(), app.status().name(), null);
    }

    public ActivityApplicationResult approve(UUID id, UUID reviewerId, UUID activityId) {
        var app = find(id); app.approve(reviewerId, activityId); repository.save(app);
        return new ActivityApplicationResult(id, app.status().name(), activityId);
    }

    public ActivityApplicationResult reject(UUID id, UUID reviewerId, String reason) {
        var app = find(id); app.reject(reviewerId, reason); repository.save(app);
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
