package com.campusguinness.interfaces.web.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record RegisterMediaRequest(
        @NotNull UUID schoolId,
        @NotNull UUID activityId,
        @NotBlank String fileKey,
        @NotBlank String fileName,
        @NotBlank String fileType,
        @NotBlank String fileFormat,
        @Positive long fileSizeBytes,
        String checksum,
        String description) {}
