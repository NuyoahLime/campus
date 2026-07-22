package com.campusguinness.interfaces.web.feedback;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** submitterId is sourced from the authenticated SecurityContext via CurrentActor. */
public record SubmitFeedbackRequest(UUID schoolId, @NotBlank String feedbackType, @NotBlank String content) {}
