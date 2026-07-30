package com.campusguinness.ranking.application.exception;

public class RankingDataConflictException extends RankingConflictException {
    public RankingDataConflictException(String message) {
        super("RANKING_DATA_CONFLICT", message);
    }
}
