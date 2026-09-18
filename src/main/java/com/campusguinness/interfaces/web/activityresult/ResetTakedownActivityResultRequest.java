package com.campusguinness.interfaces.web.activityresult;

import jakarta.validation.constraints.NotBlank;

public record ResetTakedownActivityResultRequest(
        @NotBlank String reason) {
}
