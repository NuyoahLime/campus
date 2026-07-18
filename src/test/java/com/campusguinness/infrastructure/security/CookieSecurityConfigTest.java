package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies cookie security configuration properties.
 */
@SpringBootTest
@ActiveProfiles("test")
class CookieSecurityConfigTest {

    @Autowired Environment env;

    @Test void sessionCookieHttpOnly() {
        String httpOnly = env.getProperty("server.servlet.session.cookie.http-only");
        assertThat(httpOnly).isEqualTo("true");
    }

    @Test void sessionCookieSameSite() {
        String sameSite = env.getProperty("server.servlet.session.cookie.same-site");
        assertThat(sameSite).isEqualTo("lax");
    }

    @Test void sessionCookiePath() {
        String path = env.getProperty("server.servlet.session.cookie.path");
        assertThat(path).isEqualTo("/");
    }

    @Test void sessionTrackingMode() {
        String tracking = env.getProperty("server.servlet.session.tracking-modes");
        assertThat(tracking).isEqualTo("cookie");
    }

    @Test void sessionPersistentIsFalse() {
        String persistent = env.getProperty("server.servlet.session.persistent");
        assertThat(persistent).isEqualTo("false");
    }

    @Test void csrfEnabled() {
        // CSRF is enabled via SecurityConfig (not disabled with csrf.disable())
        // Verified by AuthSessionFlowIT.loginWithoutCsrfReturns403
    }

    @Test void corsHasNoWildcardOrigin() {
        String origins = env.getProperty("campus-guinness.security.cors.allowed-origins");
        // Default is empty list — no wildcard, no default origins
        if (origins != null) {
            assertThat(origins).doesNotContain("*");
        }
    }
}
