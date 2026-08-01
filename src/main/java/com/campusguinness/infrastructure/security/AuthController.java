package com.campusguinness.infrastructure.security;

import com.campusguinness.interfaces.web.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public AuthController(AuthenticationManager authManager,
            SecurityContextRepository contextRepo,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
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
            CampusGuinnessUserDetails user = (CampusGuinnessUserDetails) auth.getPrincipal();

            var identityResult = checkIdentity(user.getResolvedIdentity(), "/api/v1/auth/login");
            if (identityResult != null) return identityResult;

            // Session fixation defense: ensure a session exists and rotate its ID.
            // request.changeSessionId() delegates to Spring Session JDBC which
            // generates a new SESSION_ID. The old SESSION_ID is no longer resolvable.
            request.getSession(true);
            request.changeSessionId();

            // Invalidate pre-login CSRF token
            sessionAuthenticationStrategy.onAuthentication(auth, request, response);

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            contextRepo.saveContext(ctx, request, response);

            return ResponseEntity.ok(AuthContextResponse.from(user));
        } catch (SessionAuthenticationException e) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                try { session.invalidate(); } catch (Exception ignored) {}
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiErrorResponse.of("AUTHENTICATION_FAILED",
                            "Authentication could not be completed.", request.getRequestURI()));
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiErrorResponse.of("AUTHENTICATION_FAILED",
                            "The username or password is invalid.", request.getRequestURI()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CampusGuinnessUserDetails user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiErrorResponse.of("AUTHENTICATION_REQUIRED",
                            "Authentication is required.", "/api/v1/auth/me"));
        }
        // Verify identity still valid
        var identityResult = checkIdentity(user.getResolvedIdentity(), "/api/v1/auth/me");
        if (identityResult != null) {
            try { var s = request.getSession(false); if (s != null) s.invalidate(); } catch (Exception ignored) {}
            return identityResult;
        }
        return ResponseEntity.ok(AuthContextResponse.from(user));
    }

    private ResponseEntity<?> checkIdentity(
            PrimaryIdentityResolver.ResolvedIdentity identity, String path) {
        if (identity == null) return identityError("IDENTITY_INVALID", path);
        if (identity.isError()) return identityError(identity.errorCode(), path);
        return null;
    }

    private ResponseEntity<ApiErrorResponse> identityError(String code, String path) {
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(code, "Account identity error.", path));
    }
}
