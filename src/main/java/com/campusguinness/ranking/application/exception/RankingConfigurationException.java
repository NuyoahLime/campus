package com.campusguinness.ranking.application.exception;

public class RankingConfigurationException extends RankingConflictException {
    public RankingConfigurationException(String message) {
        super("RANKING_CONFIGURATION_ERROR", message);
    }
}
