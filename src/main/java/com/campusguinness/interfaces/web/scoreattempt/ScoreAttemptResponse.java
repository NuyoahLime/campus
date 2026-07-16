package com.campusguinness.interfaces.web.scoreattempt;

import java.util.UUID;

public record ScoreAttemptResponse(UUID id, String status, String scoreType) {}
