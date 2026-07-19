package com.campusguinness.infrastructure.security;

import java.util.UUID;

/**
 * Application-level abstraction for obtaining the current authenticated actor's userId.
 * <p>
 * Implementations extract the principal from Spring Security's {@code SecurityContext}.
 * Application services depend on this interface, never on {@code SecurityContextHolder} directly.
 */
@FunctionalInterface
public interface CurrentActor {

    /**
     * Returns the domain User UUID of the currently authenticated actor.
     *
     * @return the actor's userId
     * @throws org.springframework.security.core.AuthenticationException if not authenticated
     * @throws IllegalStateException if the principal is of an unexpected type
     */
    UUID requireUserId();
}
