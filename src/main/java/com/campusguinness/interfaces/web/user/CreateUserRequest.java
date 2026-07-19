package com.campusguinness.interfaces.web.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String initialPassword
) {
    @Override
    public String toString() {
        return "CreateUserRequest{username='" + username + "', initialPassword=[REDACTED]}";
    }
}
