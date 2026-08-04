package com.campusguinness.identity.application.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class EmailNormalizer {

    private static final int MAX_EMAIL_LENGTH = 320;
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);

    public String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("email max 320 chars");
        }
        if (!BASIC_EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid email");
        }
        return normalized;
    }
}
