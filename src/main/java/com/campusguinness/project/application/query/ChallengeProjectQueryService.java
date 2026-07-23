package com.campusguinness.project.application.query;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.PublicProjectDetailResult;
import com.campusguinness.project.application.query.model.PublicProjectListFilter;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.project.application.query.port.ChallengeProjectQueryPort;
import com.campusguinness.project.internal.domain.ScoreStorageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChallengeProjectQueryService {
    private final ChallengeProjectQueryPort queryPort;

    public ChallengeProjectQueryService(ChallengeProjectQueryPort p) { this.queryPort = p; }

    public QueryPage<ChallengeProjectListResult> listPublic(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        return queryPort.findPublished(page, size);
    }

    public QueryPage<ChallengeProjectListResult> listPublic(PublicProjectListFilter filter, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        if (filter.scoreStorageType() != null && !filter.scoreStorageType().isBlank()) {
            try {
                ScoreStorageType.valueOf(filter.scoreStorageType());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid scoreStorageType: " + filter.scoreStorageType());
            }
        }
        return queryPort.findPublished(filter, page, size);
    }

    public Optional<PublicProjectDetailResult> findPublishedById(UUID projectId) {
        if (projectId == null) throw new IllegalArgumentException("projectId must not be null");
        return queryPort.findPublishedById(projectId);
    }
}
