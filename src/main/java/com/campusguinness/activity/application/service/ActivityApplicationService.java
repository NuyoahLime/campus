package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.SubmitActivityApplicationCommand;
import com.campusguinness.activity.application.port.ActivityApplicationRepository;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ActivityApplicationService {
    private final ActivityApplicationRepository repository;
    private final CurrentActor currentActor;
    private final SchoolMembershipResolver membershipResolver;

    public ActivityApplicationService(ActivityApplicationRepository repository,
                                       CurrentActor currentActor,
                                       SchoolMembershipResolver membershipResolver) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.membershipResolver = membershipResolver;
    }

    public ActivityApplicationResult submit(SubmitActivityApplicationCommand cmd) {
        var app = ActivityApplication.create(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(UUID.randomUUID())).schoolId(cmd.schoolId())
                .applicantId(cmd.applicantId()).title(cmd.title()).description(cmd.description()));
        app.submit();
        repository.save(app);
        return new ActivityApplicationResult(app.id().value(), app.status().name(), null);
    }

    public ActivityApplicationResult approve(UUID id, UUID reviewerId, UUID activityId) {
        var app = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, reviewerId, app.schoolId());
        }
        app.approve(reviewerId, activityId);
        repository.save(app);
        return new ActivityApplicationResult(id, app.status().name(), activityId);
    }

    public ActivityApplicationResult reject(UUID id, UUID reviewerId, String reason) {
        var app = find(id);
        if (!currentActor.isSuperAdmin()) {
            AuthorizationPolicy.requireSchoolAdmin(membershipResolver, reviewerId, app.schoolId());
        }
        app.reject(reviewerId, reason);
        repository.save(app);
        return new ActivityApplicationResult(id, app.status().name(), null);
    }

    public ActivityApplicationResult withdraw(UUID id) {
        var app = find(id); app.withdraw(); repository.save(app);
        return new ActivityApplicationResult(id, app.status().name(), null);
    }

    @Transactional(readOnly = true)
    public List<ActivityApplicationResult> findPendingBySchool(UUID schoolId) {
        return repository.findBySchoolIdAndStatus(schoolId, ApplicationStatus.SUBMITTED).stream()
                .map(a -> new ActivityApplicationResult(a.id().value(), a.status().name(), a.createdActivityId()))
                .toList();
    }

    private ActivityApplication find(UUID id) {
        return repository.findById(new ActivityApplicationId(id))
                .orElseThrow(() -> new IllegalArgumentException("ActivityApplication not found: " + id));
    }
}
