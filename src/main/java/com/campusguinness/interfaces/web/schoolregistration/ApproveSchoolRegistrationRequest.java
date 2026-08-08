package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApproveSchoolRegistrationRequest(
        String comment,
        @NotNull UUID schoolId) {}
