package com.campusguinness.interfaces.web.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateSchoolAdminRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String invitationCode,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String newPassword,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String confirmPassword
) {
    @Override
    public String toString() {
        return "ActivateSchoolAdminRequest{username='" + username
                + "', invitationCode=[REDACTED], newPassword=[REDACTED], confirmPassword=[REDACTED]}";
    }
}
