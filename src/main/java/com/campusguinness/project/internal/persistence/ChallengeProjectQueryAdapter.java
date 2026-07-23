package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.PublicProjectDetailResult;
import com.campusguinness.project.application.query.model.PublicProjectListFilter;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.project.application.query.port.ChallengeProjectQueryPort;

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
class ChallengeProjectQueryAdapter implements ChallengeProjectQueryPort {

    private final ChallengeProjectJpaRepository jpa;

    ChallengeProjectQueryAdapter(ChallengeProjectJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public QueryPage<ChallengeProjectListResult> findPublished(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        var result = jpa.findByProjectStatus("PUBLISHED", pageable);
        List<ChallengeProjectListResult> items = result.getContent().stream()
                .map(this::toListResult)
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public QueryPage<ChallengeProjectListResult> findPublished(PublicProjectListFilter filter, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        var spec = publishedFilterSpec(filter);
        var result = jpa.findAll(spec, pageable);
        List<ChallengeProjectListResult> items = result.getContent().stream()
                .map(this::toListResult)
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public Optional<PublicProjectDetailResult> findPublishedById(UUID projectId) {
        Specification<ChallengeProjectEntity> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("id"), projectId));
            predicates.add(cb.equal(root.get("projectStatus"), "PUBLISHED"));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return jpa.findOne(spec).map(this::toDetailResult);
    }

    private Specification<ChallengeProjectEntity> publishedFilterSpec(PublicProjectListFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            // Always filter PUBLISHED only
            predicates.add(cb.equal(root.get("projectStatus"), "PUBLISHED"));

            // keyword: match name OR description, case-insensitive
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword().toLowerCase() + "%";
                var nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                var descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(nameMatch, descMatch));
            }

            // category: exact match
            if (filter.category() != null && !filter.category().isBlank()) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }

            // scoreStorageType: exact match
            if (filter.scoreStorageType() != null && !filter.scoreStorageType().isBlank()) {
                predicates.add(cb.equal(root.get("scoreStorageType"), filter.scoreStorageType()));
            }

            // venueKeyword: match venueRequirements, case-insensitive
            if (filter.venueKeyword() != null && !filter.venueKeyword().isBlank()) {
                String pattern = "%" + filter.venueKeyword().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("venueRequirements")), pattern));
            }

            // equipmentKeyword: match equipmentRequirements, case-insensitive
            if (filter.equipmentKeyword() != null && !filter.equipmentKeyword().isBlank()) {
                String pattern = "%" + filter.equipmentKeyword().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("equipmentRequirements")), pattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ChallengeProjectListResult toListResult(ChallengeProjectEntity e) {
        String summary = e.getDescription();
        if (summary != null && summary.length() > 200) {
            summary = summary.substring(0, 200);
        }
        return new ChallengeProjectListResult(
                e.getId(), e.getName(), e.getCategory(), summary,
                e.getScoreStorageType(), e.getComparisonDirection(),
                e.getScoreUnit(), e.getProjectStatus(), e.getCreatedAt());
    }

    private PublicProjectDetailResult toDetailResult(ChallengeProjectEntity e) {
        return new PublicProjectDetailResult(
                e.getId(), e.getName(), e.getCategory(),
                e.getDescription(), e.getVenueRequirements(), e.getEquipmentRequirements(),
                e.getRulesText(), e.getScoreStorageType(), e.getScoreIndicatorType(),
                e.getComparisonDirection(), e.getEffectiveScoreRule(), e.isAllowTie(),
                e.getScoreUnit(), e.getDecimalPlaces(), e.getGradeOrder());
    }
}
