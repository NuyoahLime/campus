package com.campusguinness.infrastructure.security;

import com.campusguinness.interfaces.web.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepo;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthController(
            AuthenticationManager authManager,
            SecurityContextRepository contextRepo,
            SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) {
        this.authManager = authManager;
        this.contextRepo = contextRepo;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        try {
            var token = UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password());
            Authentication auth = authManager.authenticate(token);
            request.getSession(true);
            request.changeSessionId();
            sessionAuthenticationStrategy.onAuthentication(auth, request, response);

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            contextRepo.saveContext(ctx, request, response);

            CampusGuinnessUserDetails user = (CampusGuinnessUserDetails) auth.getPrincipal();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(LoginResponse.from(user));
        } catch (SessionAuthenticationException e) {
            clearAuthenticationState(request);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiErrorResponse.of("AUTHENTICATION_FAILED",
                            "Authentication could not be completed.", request.getRequestURI()));
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            if (e instanceof LoginDeniedAuthenticationException denied) {
                return ResponseEntity.status(denied.status())
                        .cacheControl(CacheControl.noStore())
                        .body(ApiErrorResponse.of(denied.code(), denied.getMessage(), request.getRequestURI()));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiErrorResponse.of("AUTHENTICATION_FAILED",
                            "The username or password is invalid.", request.getRequestURI()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CampusGuinnessUserDetails user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiErrorResponse.of("AUTHENTICATION_REQUIRED",
                            "Authentication is required.", request.getRequestURI()));
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(LoginResponse.from(user));
    }

    private void clearAuthenticationState(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // Session may already have been invalidated by the container.
            }
        }
    }
}
