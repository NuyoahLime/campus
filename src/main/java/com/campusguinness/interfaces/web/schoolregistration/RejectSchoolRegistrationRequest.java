package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotBlank;

public record RejectSchoolRegistrationRequest(
        @NotBlank String reason) {}
