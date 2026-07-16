package com.campusguinness.project.application.query;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.project.application.query.port.ChallengeProjectQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
