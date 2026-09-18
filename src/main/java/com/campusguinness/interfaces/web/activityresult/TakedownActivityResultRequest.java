package com.campusguinness.interfaces.web.activityresult;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TakedownActivityResultRequest(
        @NotBlank @Size(max = 2000) String reason) {
}
