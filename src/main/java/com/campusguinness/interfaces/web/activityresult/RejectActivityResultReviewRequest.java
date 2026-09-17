package com.campusguinness.interfaces.web.activityresult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectActivityResultReviewRequest(
        @NotBlank @Size(max = 2000) String reason) {
}
