package com.campusguinness.interfaces.web.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StudentRegistrationRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String confirmPassword,
        @NotBlank @Size(max = 100) String realName,
        @NotNull UUID schoolId,
        @NotBlank @Size(max = 64) String studentNumber,
        @NotBlank @Size(max = 32) String grade,
        @NotBlank @Size(max = 64) String className,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> proofFileKeys
) {
    @Override
    public String toString() {
        return "StudentRegistrationRequest{username='" + username
                + "', password=[REDACTED], confirmPassword=[REDACTED], schoolId=" + schoolId + "}";
    }
}
