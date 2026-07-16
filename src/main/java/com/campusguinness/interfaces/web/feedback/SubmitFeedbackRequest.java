package com.campusguinness.interfaces.web.feedback;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record SubmitFeedbackRequest(UUID schoolId, UUID submitterId, @NotBlank String feedbackType, @NotBlank String content) {}
