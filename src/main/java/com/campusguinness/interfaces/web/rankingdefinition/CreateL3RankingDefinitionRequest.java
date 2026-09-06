package com.campusguinness.interfaces.web.rankingdefinition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateL3RankingDefinitionRequest(
        @NotBlank String name,
        @NotNull UUID projectId,
        @NotNull UUID ruleVersionId) {
}
