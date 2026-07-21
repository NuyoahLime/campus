package com.campusguinness.infrastructure.security;

import org.springframework.stereotype.Component;

/**
 * Single source of truth for login identifier normalization.
 * Must match CampusGuinnessUserDetailsService behavior exactly.
 */
@Component
public class LoginIdentifierNormalizer {

    /** Trim whitespace. Matches CampusGuinnessUserDetailsService.normalizeLoginName(). */
    public String normalize(String raw) {
        return raw != null ? raw.trim() : "";
    }
}
