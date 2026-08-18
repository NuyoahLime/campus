package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ActivityProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Component
class ActivityProjectRepositoryAdapter implements ActivityProjectRepository {
    private final ActivityProjectJpaRepository repository;
    ActivityProjectRepositoryAdapter(ActivityProjectJpaRepository repository) { this.repository = repository; }
    @Override @Transactional
    public void save(ActivityProjectSnapshot snapshot) {
        var entity = new ActivityProjectEntity();
        entity.setId(snapshot.id()); entity.setActivityId(snapshot.activityId());
        entity.setProjectId(snapshot.projectId()); entity.setRuleVersionId(snapshot.ruleVersionId());
        entity.setCreatedAt(Instant.now()); repository.save(entity);
    }
}
