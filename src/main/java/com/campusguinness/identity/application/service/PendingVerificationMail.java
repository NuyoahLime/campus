package com.campusguinness.identity.application.service;

public record PendingVerificationMail(
        String email,
        String rawToken
) {}
