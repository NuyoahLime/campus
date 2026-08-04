package com.campusguinness.infrastructure.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

/**
 * Generates one-time temporary passwords for newly provisioned accounts.
 *
 * <p>Uses {@link SecureRandom} only — never {@code java.util.Random},
 * {@code UUID.randomUUID()}, or username-derived values.
 *
 * <p>Generated passwords:
 * <ul>
 *   <li>at least 16 characters long</li>
 *   <li>contain at least one uppercase letter, one lowercase letter, and one digit</li>
 *   <li>never appear in logs or audit records</li>
 * </ul>
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";   // no I/O
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";   // no l/o
    private static final String DIGITS = "23456789";                   // no 0/1
    private static final String ALL = UPPER + LOWER + DIGITS;

    private static final int MIN_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a cryptographically random temporary password.
     *
     * @return a password of at least {@value #MIN_LENGTH} characters
     *         mixing uppercase, lowercase, and digits
     */
    public String generate() {
        // Guarantee at least one of each required character class
        StringBuilder sb = new StringBuilder(MIN_LENGTH);
        sb.append(pick(UPPER));
        sb.append(pick(LOWER));
        sb.append(pick(DIGITS));

        // Fill the rest randomly
        for (int i = sb.length(); i < MIN_LENGTH; i++) {
            sb.append(pick(ALL));
        }

        // Shuffle so the guaranteed positions aren't predictable
        List<Character> chars = sb.chars().mapToObj(c -> (char) c).collect(java.util.stream.Collectors.toList());
        java.util.Collections.shuffle(chars, secureRandom);
        return chars.stream().collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(secureRandom.nextInt(alphabet.length()));
    }

    @Override
    public String toString() {
        return "TemporaryPasswordGenerator{...}";
    }
}
