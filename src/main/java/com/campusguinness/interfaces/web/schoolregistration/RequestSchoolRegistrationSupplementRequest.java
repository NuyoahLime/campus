package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestSchoolRegistrationSupplementRequest(
        @NotBlank @Size(max = 2000) String comment
) {}
