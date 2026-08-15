package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.Size;

public record ApproveSchoolRegistrationRequest(
        @Size(max = 2000) String comment
) {}
