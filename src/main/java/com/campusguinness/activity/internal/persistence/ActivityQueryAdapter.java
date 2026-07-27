package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ActivityQueryAdapter implements ActivityQueryPort {

    private final ActivityJpaRepository jpa;
    ActivityQueryAdapter(ActivityJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public QueryPage<ActivityListResult> findPublic(int page, int size, List<String> statuses) {
        var pageable = PageRequest.of(page, size, Sort.by("startTime").descending().and(Sort.by("id").descending()));
        var result = jpa.findByExecutionStatusIn(statuses, pageable);
        return toPage(result);
    }

    @Override
    public QueryPage<ActivityListResult> findPublicPublished(int page, int size, List<String> executionStatuses) {
        var pageable = PageRequest.of(page, size, Sort.by("startTime").descending().and(Sort.by("id").descending()));
        var result = jpa.findByExecutionStatusInAndPublicStatus(executionStatuses, "PUBLIC", pageable);
        return toPage(result);
    }

    @Override
    public QueryPage<ActivityListResult> findBySchool(UUID schoolId, String executionStatus,
            String publicStatus, String keyword, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        var spec = schoolFilterSpec(schoolId, executionStatus, publicStatus, keyword);
        var result = jpa.findAll(spec, pageable);
        return toPage(result);
    }

    @Override
    public Optional<ActivityListResult> findByIdAndSchoolId(UUID activityId, UUID schoolId) {
        Specification<ActivityEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("id"), activityId));
            predicates.add(cb.equal(root.get("schoolId"), schoolId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return jpa.findOne(spec).map(this::toResult);
    }

    @Override
    public QueryPage<ActivityListResult> findPublicReview(String schoolId, String publicStatus,
            int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        String targetPublicStatus = (publicStatus != null && !publicStatus.isBlank())
                ? publicStatus : "PENDING_PLATFORM_REVIEW";
        var spec = publicReviewSpec(schoolId, targetPublicStatus);
        var result = jpa.findAll(spec, pageable);
        return toPage(result);
    }

    // ── specs ──

    private Specification<ActivityEntity> schoolFilterSpec(UUID schoolId, String executionStatus,
            String publicStatus, String keyword) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("schoolId"), schoolId));
            if (executionStatus != null && !executionStatus.isBlank()) {
                predicates.add(cb.equal(root.get("executionStatus"), executionStatus));
            }
            if (publicStatus != null && !publicStatus.isBlank()) {
                predicates.add(cb.equal(root.get("publicStatus"), publicStatus));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ActivityEntity> publicReviewSpec(String schoolId, String publicStatus) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("publicStatus"), publicStatus));
            if (schoolId != null && !schoolId.isBlank()) {
                predicates.add(cb.equal(root.get("schoolId"), UUID.fromString(schoolId)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── helpers ──

    private QueryPage<ActivityListResult> toPage(org.springframework.data.domain.Page<ActivityEntity> result) {
        var items = result.getContent().stream().map(this::toResult).toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private ActivityListResult toResult(ActivityEntity e) {
        return new ActivityListResult(e.getId(), e.getSchoolId(), e.getTitle(),
                e.getStartTime(), e.getEndTime(), e.getLocation(), e.getExecutionStatus(),
                e.getPublicStatus());
    }
}
