package com.campusguinness.result.application.service;

import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.internal.domain.*;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ActivityResultApplicationService {
    private final ActivityResultRepository repository;
    private final SchoolResourceAuthorization authorization;

    public ActivityResultApplicationService(ActivityResultRepository r, SchoolResourceAuthorization authorization) {
        this.repository = r;
        this.authorization = authorization;
    }

    public ActivityResultResult create(UUID schoolId, UUID activityId) {
        var r = ActivityResult.create(new ActivityResult.Builder()
                .id(new ActivityResultId(UUID.randomUUID())).schoolId(schoolId).activityId(activityId));
        repository.save(r);
        return new ActivityResultResult(r.id().value(), r.internalStatus().name(), r.publicStatus().name());
    }

    public ActivityResultResult publishInternal(UUID id) {
        var r = find(id);
        authorization.requireSchoolAdmin(r.schoolId());
        r.publishInternal();
        repository.save(r);
        return new ActivityResultResult(id, r.internalStatus().name(), r.publicStatus().name());
    }

    private ActivityResult find(UUID id) {
        return repository.findById(new ActivityResultId(id))
                .orElseThrow(() -> new IllegalArgumentException("ActivityResult not found: " + id));
    }
}
