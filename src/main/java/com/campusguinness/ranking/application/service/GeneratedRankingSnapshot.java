package com.campusguinness.ranking.application.service;

import java.util.List;
import java.util.UUID;

public record GeneratedRankingSnapshot(
        String tiePolicy,
        List<GeneratedRankingEntry> entries,
        List<UUID> authorizationIdsSnapshot
) {
    public GeneratedRankingSnapshot(String tiePolicy, List<GeneratedRankingEntry> entries) {
        this(tiePolicy, entries, List.of());
    }

    public int entryCount() {
        return entries.size();
    }
}
