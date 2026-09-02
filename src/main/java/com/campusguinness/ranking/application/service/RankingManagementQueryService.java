package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingManagementDefinitionResult;
import com.campusguinness.ranking.application.query.port.RankingManagementQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RankingManagementQueryService {
    private final RankingManagementQueryPort queryPort;
    private final SchoolResourceAuthorization authorization;

    public RankingManagementQueryService(
            RankingManagementQueryPort queryPort,
            SchoolResourceAuthorization authorization) {
        this.queryPort = queryPort;
        this.authorization = authorization;
    }

    public QueryPage<RankingManagementDefinitionResult> list(int page, int size) {
        validatePage(page, size);
        return queryPort.list(authorization.requireUniqueSchoolAdminSchool(), page, size);
    }

    public RankingManagementDefinitionResult detail(UUID definitionId) {
        if (definitionId == null) {
            throw new IllegalArgumentException("rankingDefinitionId required");
        }
        UUID schoolId = authorization.requireUniqueSchoolAdminSchool();
        return queryPort.detail(definitionId, schoolId)
                .orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + definitionId));
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
}
