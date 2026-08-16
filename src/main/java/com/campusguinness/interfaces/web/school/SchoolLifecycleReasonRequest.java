package com.campusguinness.interfaces.web.school;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchoolLifecycleReasonRequest(
        @NotBlank
        @Size(min = 2, max = 500)
        String reason
) {
}
