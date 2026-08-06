package com.campusguinness.interfaces.web.studentidentityapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectStudentIdentityApplicationRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
