package com.campusguinness.infrastructure.security;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the current CSRF token for SPA consumption.
 * Token is delivered via the XSRF-TOKEN cookie (set by CookieCsrfTokenRepository)
 * and returned in the response body.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/v1/auth/csrf")
    public CsrfTokenResponse csrf(CsrfToken token) {
        return new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    public record CsrfTokenResponse(String headerName, String parameterName, String token) {}
}
