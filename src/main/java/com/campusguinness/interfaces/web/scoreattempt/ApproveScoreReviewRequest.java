package com.campusguinness.interfaces.web.scoreattempt;

import jakarta.validation.constraints.Size;

public record ApproveScoreReviewRequest(
        @Size(max = 2000) String reviewComment,
        Boolean makeCurrentEffective) {
}
