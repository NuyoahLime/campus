package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateActivityApplicationRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description) {}
