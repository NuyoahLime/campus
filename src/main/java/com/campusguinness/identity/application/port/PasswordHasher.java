package com.campusguinness.identity.application.port;

/**
 * Application-level password hashing abstraction.
 * <p>
 * Domain layer must never depend on this.
 * Controller must never call BCrypt directly.
 * Infrastructure implements this using Spring Security's PasswordEncoder.
 */
public interface PasswordHasher {

    /**
     * Hash a raw password using the configured algorithm.
     *
     * @param rawPassword the raw password (must be pre-validated)
     * @return the encoded hash
     */
    String hash(String rawPassword);

    /**
     * Verify a raw password against a stored hash.
     *
     * @param rawPassword  the raw password to check
     * @param passwordHash the stored hash
     * @return true if the raw password matches the hash
     */
    boolean matches(String rawPassword, String passwordHash);
}
