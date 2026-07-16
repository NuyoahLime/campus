package com.campusguinness.result.application.service;

import com.campusguinness.result.application.port.ActivityResultRepository;
import com.campusguinness.result.application.result.ActivityResultResult;
import com.campusguinness.result.internal.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class ActivityResultApplicationService {
    private final ActivityResultRepository repository;
    public ActivityResultApplicationService(ActivityResultRepository r) { this.repository = r; }

    public ActivityResultResult create(UUID schoolId, UUID activityId) {
        var r = ActivityResult.create(new ActivityResult.Builder()
                .id(new ActivityResultId(UUID.randomUUID())).schoolId(schoolId).activityId(activityId));
        repository.save(r);
        return new ActivityResultResult(r.id().value(), r.internalStatus().name(), r.publicStatus().name());
    }

    public ActivityResultResult publishInternal(UUID id) {
        var r = find(id); r.publishInternal(); repository.save(r);
        return new ActivityResultResult(id, r.internalStatus().name(), r.publicStatus().name());
    }

    private ActivityResult find(UUID id) {
        return repository.findById(new ActivityResultId(id))
                .orElseThrow(() -> new IllegalArgumentException("ActivityResult not found: " + id));
    }
}
