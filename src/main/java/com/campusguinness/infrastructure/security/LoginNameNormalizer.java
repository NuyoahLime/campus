package com.campusguinness.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class LoginNameNormalizer {
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("username must not be blank");
        return raw.trim();
    }
}
