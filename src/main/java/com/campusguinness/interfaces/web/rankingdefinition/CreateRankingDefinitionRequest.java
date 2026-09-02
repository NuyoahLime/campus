package com.campusguinness.interfaces.web.rankingdefinition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateRankingDefinitionRequest(
        @NotBlank String layer,
        @NotBlank @Size(max = 200) String name,
        UUID schoolId,
        @NotNull UUID projectId,
        UUID activityProjectId,
        String dimensionFilters) {
    public CreateRankingDefinitionRequest(
            String layer,
            String name,
            UUID schoolId,
            UUID projectId,
            UUID activityProjectId) {
        this(layer, name, schoolId, projectId, activityProjectId, null);
    }
}
