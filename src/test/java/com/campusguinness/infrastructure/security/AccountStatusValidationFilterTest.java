package com.campusguinness.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountStatusValidationFilterTest {

    private JdbcTemplate jdbc;

    private AccountStatusValidationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        filter = new AccountStatusValidationFilter(jdbc);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    private void setAuthenticatedPrincipal() {
        var user = new CampusGuinnessUserDetails(USER_ID, USERNAME, "hash", "NORMAL", null,
                Set.of(new SimpleGrantedAuthority("ROLE_STUDENT")),
                java.util.List.of(),
                new PrimaryIdentityResolver.ResolvedIdentity(USER_ID, "STUDENT", null, "NORMAL"));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── No auth ──

    @Test
    void unauthenticatedRequestPassesThrough() throws Exception {
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(jdbc);
    }

    // ── NORMAL ──

    @Test
    void normalAccountPassesThrough() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID))).thenReturn("NORMAL");

        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ── Rejected statuses ──

    @Test
    void disabledAccountIsRejected() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID))).thenReturn("DISABLED");

        filter.doFilterInternal(request, response, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void lockedAccountIsRejected() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID))).thenReturn("LOCKED");

        filter.doFilterInternal(request, response, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void pendingActivationAccountIsRejected() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID))).thenReturn("PENDING_ACTIVATION");

        filter.doFilterInternal(request, response, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void unknownStatusIsRejected() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID))).thenReturn("SOME_FUTURE_STATUS");

        filter.doFilterInternal(request, response, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void nullStatusIsRejected() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID))).thenReturn(null);

        filter.doFilterInternal(request, response, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ── Database failures ──

    @Test
    void emptyResultRejectsWith401() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID)))
                .thenThrow(new EmptyResultDataAccessException("no row", 1));

        filter.doFilterInternal(request, response, chain);
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ACCOUNT_NOT_FOUND");
    }

    @Test
    void databaseFailureIsFailClosed() throws Exception {
        setAuthenticatedPrincipal();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(USER_ID)))
                .thenThrow(new DataAccessException("connection lost") {});

        filter.doFilterInternal(request, response, chain);
        // Must NOT continue the filter chain
        verify(chain, never()).doFilter(any(), any());
        // Must return 503, not 401 (to distinguish from disabled)
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("ACCOUNT_STATUS_UNAVAILABLE");
    }

    // ── Skip public paths ──

    @Test
    void skipCsrfPath() {
        request.setRequestURI("/api/v1/auth/csrf");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void skipLoginPath() {
        request.setRequestURI("/api/v1/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void doNotSkipAuthenticatedPath() {
        request.setRequestURI("/api/v1/auth/me");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doNotSkipStudentPath() {
        request.setRequestURI("/api/v1/student/scores");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
