package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.application.query.model.QueryPage;
import com.campusguinness.activity.application.query.port.ActivityApplicationQueryPort;
import com.campusguinness.activity.application.result.ActivityApplicationResult;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class ActivityApplicationQueryAdapter implements ActivityApplicationQueryPort {

    private final ActivityApplicationJpaRepository jpa;

    ActivityApplicationQueryAdapter(ActivityApplicationJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public QueryPage<ActivityApplicationResult> findAll(String status, UUID schoolId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        var spec = buildSpec(status, schoolId);
        var result = jpa.findAll(spec, pageable);
        var items = result.getContent().stream()
                .map(e -> ActivityApplicationResult.fromDomain(ActivityApplicationPersistenceMapper.toDomain(e)))
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public Optional<ActivityApplicationResult> findById(UUID id) {
        return jpa.findById(id)
                .map(ActivityApplicationPersistenceMapper::toDomain)
                .map(ActivityApplicationResult::fromDomain);
    }

    private Specification<ActivityApplicationEntity> buildSpec(String status, UUID schoolId) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("applicationStatus"), status));
            }
            if (schoolId != null) {
                predicates.add(cb.equal(root.get("schoolId"), schoolId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
