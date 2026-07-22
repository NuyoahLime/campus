package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.port.ActivityProjectPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ActivityProjectAdapter implements ActivityProjectPort {
    private final ActivityProjectJpaRepository jpa;

    ActivityProjectAdapter(ActivityProjectJpaRepository jpa) { this.jpa = jpa; }

    @Override @Transactional
    public ProjectRecord add(UUID activityId, UUID projectId, UUID ruleVersionId) {
        var e = new ActivityProjectEntity();
        e.setId(UUID.randomUUID());
        e.setActivityId(activityId);
        e.setProjectId(projectId);
        e.setRuleVersionId(ruleVersionId);
        e.setCreatedAt(Instant.now());
        jpa.save(e);
        return toRecord(e);
    }

    @Override @Transactional(readOnly = true)
    public List<ProjectRecord> findByActivity(UUID activityId) {
        return jpa.findByActivityId(activityId).stream().map(this::toRecord).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<ProjectRecord> findByActivityAndProject(UUID activityId, UUID projectId) {
        return jpa.findByActivityIdAndProjectId(activityId, projectId).map(this::toRecord);
    }

    @Override @Transactional
    public void remove(UUID activityId, UUID projectId) {
        jpa.findByActivityIdAndProjectId(activityId, projectId).ifPresent(jpa::delete);
    }

    @Override @Transactional(readOnly = true)
    public boolean existsByActivityAndProject(UUID activityId, UUID projectId) {
        return jpa.existsByActivityIdAndProjectId(activityId, projectId);
    }

    private ProjectRecord toRecord(ActivityProjectEntity e) {
        return new ProjectRecord(e.getId(), e.getActivityId(), e.getProjectId());
    }
}
