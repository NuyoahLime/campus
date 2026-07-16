package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.project.application.query.port.ChallengeProjectQueryPort;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                .map(e -> new ChallengeProjectListResult(e.getId(), e.getName(), e.getCategory(),
                        e.getScoreStorageType(), e.getComparisonDirection(), e.getProjectStatus(), e.getCreatedAt()))
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
