package com.campusguinness.interfaces.web.feedback;

import jakarta.validation.constraints.NotBlank;

public record ResolveFeedbackRequest(@NotBlank String reply) {}
