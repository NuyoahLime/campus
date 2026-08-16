package com.campusguinness.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher AUTHENTICATED_LOGOUT_REQUEST_MATCHER = request -> {
        if (!HttpMethod.POST.matches(request.getMethod())
                || !"/api/v1/auth/logout".equals(request.getRequestURI())) {
            return false;
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof CampusGuinnessUserDetails;
    };

    private final SecurityCorsProperties corsProps;

    public SecurityConfig(SecurityCorsProperties corsProps) {
        this.corsProps = corsProps;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        var csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return csrfRepo;
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new CsrfAuthenticationStrategy(csrfTokenRepository)
        ));
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository repo,
            CsrfTokenRepository csrfTokenRepository
    ) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                .accessDeniedHandler(new JsonAccessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/student/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/student/resubmit").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/school-admin/activate").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/schools").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/school-registrations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/challenge-projects").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/challenge-projects/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/activities").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/schools/*/student-identity-applications",
                        "/api/v1/schools/*/student-identity-applications/*"
                ).hasRole("SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/schools/*/student-identity-applications/*/approve",
                        "/api/v1/schools/*/student-identity-applications/*/reject",
                        "/api/v1/activities",
                        "/api/v1/activities/*/publish",
                        "/api/v1/activity-applications/*/approve",
                        "/api/v1/activity-applications/*/reject",
                        "/api/v1/activity-results/*/publish",
                        "/api/v1/score-appeals/*/begin-processing",
                        "/api/v1/score-appeals/*/reject",
                        "/api/v1/ranking-definitions",
                        "/api/v1/ranking-definitions/*/enable",
                        "/api/v1/ranking-definitions/*/disable",
                        "/api/v1/l3-authorizations",
                        "/api/v1/l3-authorizations/*/withdraw",
                        "/api/v1/media/*/internal-approve",
                        "/api/v1/feedbacks/*/begin-processing",
                        "/api/v1/feedbacks/*/resolve"
                ).hasRole("SCHOOL_ADMIN")
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/schools/*/activate",
                        "/api/v1/schools/*/disable",
                        "/api/v1/school-registrations/*/request-supplement",
                        "/api/v1/school-registrations/*/approve",
                        "/api/v1/school-registrations/*/reject",
                        "/api/v1/challenge-projects",
                        "/api/v1/challenge-projects/*/publish",
                        "/api/v1/school-admin-invitations",
                        "/api/v1/school-admin-invitations/*/revoke",
                        "/api/v1/school-admin-invitations/*/regenerate",
                        "/api/v1/l3-authorizations/*/approve"
                ).hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/schools/*",
                        "/api/v1/schools/*/school-admins",
                        "/api/v1/schools/*/school-admin-invitations",
                        "/api/v1/schools/*/school-admin-invitations/*"
                ).hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/score-appeals",
                        "/api/v1/score-appeals/*/withdraw",
                        "/api/v1/feedbacks",
                        "/api/v1/feedbacks/*/close"
                ).hasRole("STUDENT")
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/school-registrations/*/withdraw",
                        "/api/v1/activity-applications",
                        "/api/v1/activity-applications/*/withdraw",
                        "/api/v1/score-attempts",
                        "/api/v1/media",
                        "/api/v1/media/*/internal-review"
                ).denyAll()
                .requestMatchers(
                        "/api/v1/users",
                        "/api/v1/users/**"
                ).hasRole("SUPER_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutRequestMatcher(AUTHENTICATED_LOGOUT_REQUEST_MATCHER)
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("SESSION", "XSRF-TOKEN"))
            .securityContext(ctx -> ctx.securityContextRepository(repo))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .requestCache(cache -> cache.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(corsProps.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(corsProps.getMaxAge());
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
