package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerLockoutFailureTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock SecurityContextRepository contextRepository;
    @Mock SessionAuthenticationStrategy sessionStrategy;
    @Mock LoginAttemptService loginAttemptService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authenticationManager, contextRepository,
                sessionStrategy, loginAttemptService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test void stateResetFailureDoesNotPersistAuthenticatedSession() {
        UUID userId = UUID.randomUUID();
        var identity = new PrimaryIdentityResolver.ResolvedIdentity(userId, "SUPER_ADMIN", null, "NORMAL");
        var principal = new CampusGuinnessUserDetails(userId, "admin", "hash", "NORMAL",
                null, Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")),
                List.of(), identity);

        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        doThrow(new AuthenticationStateUnavailableException("state unavailable"))
                .when(loginAttemptService).recordSuccess("admin");

        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        var response = new MockHttpServletResponse();

        ResponseEntity<?> result = controller.login(
                new LoginRequest("admin", "password123"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(sessionStrategy, contextRepository);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
    }
}
