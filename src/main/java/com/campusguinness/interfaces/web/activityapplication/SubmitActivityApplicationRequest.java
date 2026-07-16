package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitActivityApplicationRequest(
        @NotNull UUID schoolId,
        @NotNull UUID applicantId,
        @NotBlank @Size(max = 200) String title,
        String description) {}
