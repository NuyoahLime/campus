package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SecurityContextCurrentActorTest {

    private final SecurityContextCurrentActor currentActor = new SecurityContextCurrentActor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test void returnsUserIdFromAuthenticatedPrincipal() {
        UUID userId = UUID.randomUUID();
        var principal = new CampusGuinnessUserDetails(userId, "u", "h", "NORMAL", Set.of(), java.util.List.of());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(currentActor.requireUserId()).isEqualTo(userId);
    }

    @Test void throwsWhenNoAuthentication() {
        assertThatThrownBy(currentActor::requireUserId)
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test void throwsWhenAnonymous() {
        var anon = new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anon);

        assertThatThrownBy(currentActor::requireUserId)
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test void throwsWhenWrongPrincipalType() {
        var auth = new UsernamePasswordAuthenticationToken("justAString", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(currentActor::requireUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected principal type");
    }
}
