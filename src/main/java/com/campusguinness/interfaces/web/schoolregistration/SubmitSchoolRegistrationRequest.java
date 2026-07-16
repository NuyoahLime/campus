package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitSchoolRegistrationRequest(
        @NotBlank @Size(max = 200) String schoolName,
        @NotBlank String unifiedCodeType,
        String unifiedCode,
        @NotBlank String schoolType,
        @NotBlank String region,
        @NotBlank String address,
        @NotBlank @Size(max = 100) String contactName,
        @NotBlank @Size(max = 32) String contactPhone,
        @NotBlank @Size(max = 200) String contactEmail,
        String description,
        String evidenceFileKey) {}
