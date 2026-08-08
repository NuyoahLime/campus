package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotBlank;

public record RejectActivityApplicationRequest(@NotBlank String reason) {}
