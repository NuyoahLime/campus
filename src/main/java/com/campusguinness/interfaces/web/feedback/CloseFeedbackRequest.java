package com.campusguinness.interfaces.web.feedback;

import jakarta.validation.constraints.NotBlank;

public record CloseFeedbackRequest(@NotBlank String reason) {}
