package com.campusguinness.infrastructure.security;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonical username normalization shared by login lookup and account creation.
 * <p>
 * Rules: trim, Unicode NFKC normalization, lowercase (Locale.ROOT).
 * Must be the single source of truth for all username comparisons.
 */
@Component
public class LoginNameNormalizer {

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("username must not be blank");
        String trimmed = raw.trim();
        String nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }
}
