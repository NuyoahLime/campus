package com.campusguinness.project.application.result;

import java.util.UUID;

/** Result returned after successful ChallengeProject creation. */
public record ChallengeProjectResult(UUID id, String name, String status) {}
