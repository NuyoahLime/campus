package com.campusguinness.infrastructure.security;

public record RegisterResponse(
        String username,
        boolean verificationRequired,
        String nextAction
) {}
