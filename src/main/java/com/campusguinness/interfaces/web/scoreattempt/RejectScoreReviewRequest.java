package com.campusguinness.interfaces.web.scoreattempt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectScoreReviewRequest(
        @NotBlank @Size(max = 1000) String rejectReason,
        @Size(max = 2000) String reviewComment) {
}
