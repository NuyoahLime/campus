package com.campusguinness.ranking.application.query.port;

import com.campusguinness.ranking.application.query.model.RankingScoreSource;

import java.util.List;
import java.util.UUID;

public interface RankingScoreSourceQueryPort {
    List<RankingScoreSource> findCurrentEffectiveApprovedSources(
            UUID schoolId, UUID activityProjectId);

    List<RankingScoreSource> lockCurrentEffectiveApprovedSources(
            UUID schoolId, UUID activityProjectId);
}
