package com.campusguinness.interfaces.web.l3authorization;

import jakarta.validation.constraints.NotBlank;

public record WithdrawL3AuthorizationRequest(@NotBlank String reason) {}
