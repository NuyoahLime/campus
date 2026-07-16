package com.campusguinness.interfaces.web.l3authorization;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateL3AuthorizationRequest(
        @NotNull UUID schoolId, @NotNull UUID projectId, @NotNull UUID ruleVersionId) {}
