package com.campusguinness.ranking.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadSummaryResult;

import java.util.Optional;
import java.util.UUID;

public interface RankingReadQueryPort {
    QueryPage<RankingReadSummaryResult> list(UUID schoolId, boolean includeGlobal, int page, int size);

    Optional<RankingReadResult> detail(UUID rankingId, UUID schoolId, boolean includeGlobal);
}
