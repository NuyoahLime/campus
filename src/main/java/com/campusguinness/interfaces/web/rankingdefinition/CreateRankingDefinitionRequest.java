package com.campusguinness.interfaces.web.rankingdefinition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateRankingDefinitionRequest(
        @NotBlank String layer,
        @NotBlank @Size(max = 200) String name,
        UUID schoolId,
        @NotNull UUID projectId) {}
