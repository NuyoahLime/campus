package com.campusguinness.ranking.application.service;

import java.util.List;

public record GeneratedRankingSnapshot(
        String tiePolicy,
        List<GeneratedRankingEntry> entries
) {
    public int entryCount() {
        return entries.size();
    }
}
