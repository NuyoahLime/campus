package com.campusguinness.score.application.result;
import java.util.UUID;
public record ScoreAttemptResult(UUID id, String status, String scoreType) {}
