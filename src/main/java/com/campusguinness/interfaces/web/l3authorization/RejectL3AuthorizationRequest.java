package com.campusguinness.interfaces.web.l3authorization;

import jakarta.validation.constraints.NotBlank;

public record RejectL3AuthorizationRequest(@NotBlank String reason) {
}
