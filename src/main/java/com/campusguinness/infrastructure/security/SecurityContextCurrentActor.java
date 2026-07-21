package com.campusguinness.infrastructure.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Extracts the current actor's userId from Spring Security's {@code SecurityContext}.
 * <p>
 * Never reads actorId from request headers, request body, or {@code Authentication.getName()}.
 * Only accepts {@link CampusGuinnessUserDetails} as the principal type.
 */
@Component
public class SecurityContextCurrentActor implements CurrentActor {

    @Override
    public UUID requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new org.springframework.security.core.AuthenticationException("Not authenticated") {};
        }

        if (!(auth.getPrincipal() instanceof CampusGuinnessUserDetails userDetails)) {
            throw new IllegalStateException(
                    "Unexpected principal type: " + auth.getPrincipal().getClass().getName()
                            + ". Expected CampusGuinnessUserDetails.");
        }

        return userDetails.getUserId();
    }

    @Override
    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CampusGuinnessUserDetails userDetails) {
            return userDetails.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        }
        return false;
    }
}
