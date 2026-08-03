package com.campusguinness.interfaces.web.feedback;

import jakarta.validation.constraints.NotBlank;

public record SubmitFeedbackRequest(@NotBlank String feedbackType, @NotBlank String content) {}
