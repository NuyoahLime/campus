package com.campusguinness.ranking.application.exception;

public class RankingSourceChangedException extends RankingConflictException {
    public RankingSourceChangedException() {
        super("RANKING_SOURCE_CHANGED", "Ranking score sources changed after preview");
    }
}
