package com.campusguinness.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Validates account status on every authenticated request.
 * <p>
 * Only {@code NORMAL} accounts pass through. Any other status
 * (DISABLED, LOCKED, PENDING_ACTIVATION, null) is rejected with 401.
 * Database lookup failures return 503 to avoid fail-open behavior.
 */
public class AccountStatusValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusValidationFilter.class);

    private final JdbcTemplate jdbc;

    public AccountStatusValidationFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx == null ? null : ctx.getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof CampusGuinnessUserDetails user)) {
            chain.doFilter(request, response);
            return;
        }

        UUID userId = user.getUserId();
        String status;
        try {
            status = jdbc.queryForObject(
                    "SELECT account_status FROM users WHERE id = ?",
                    String.class, userId);
        } catch (EmptyResultDataAccessException e) {
            rejectAccount(request, response, "ACCOUNT_NOT_FOUND");
            return;
        } catch (DataAccessException e) {
            log.error("Account status lookup failed for user {}", userId, e);
            rejectStatusUnavailable(response);
            return;
        }

        if (!"NORMAL".equals(status)) {
            rejectAccount(request, response, "ACCOUNT_NOT_ACTIVE");
            return;
        }

        chain.doFilter(request, response);
    }

    private void rejectAccount(HttpServletRequest request, HttpServletResponse response,
            String code) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            try { session.invalidate(); } catch (Exception ignored) {}
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"Account is not active.\"}");
    }

    private void rejectStatusUnavailable(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"ACCOUNT_STATUS_UNAVAILABLE\",\"message\":\"Account status could not be verified.\"}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/")
                || path.equals("/api/v1/auth/csrf")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/activate")
                || path.startsWith("/api/v1/public/");
    }
}
