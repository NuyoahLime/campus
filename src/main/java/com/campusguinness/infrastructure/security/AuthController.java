package com.campusguinness.infrastructure.security;

import com.campusguinness.interfaces.web.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepo;

    public AuthController(AuthenticationManager authManager, SecurityContextRepository contextRepo) {
        this.authManager = authManager;
        this.contextRepo = contextRepo;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        try {
            var token = UsernamePasswordAuthenticationToken.unauthenticated(req.username(), req.password());
            Authentication auth = authManager.authenticate(token);
            CampusGuinnessUserDetails user = (CampusGuinnessUserDetails) auth.getPrincipal();

            // Check primary identity before creating session
            var identity = user.getResolvedIdentity();
            if (identity != null && identity.isError()) {
                SecurityContextHolder.clearContext();
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiErrorResponse.of(identity.errorCode(),
                                "Account identity error: " + identity.errorCode(), "/api/v1/auth/login"));
            }

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            request.getSession(true);
            contextRepo.saveContext(ctx, request, response);

            return ResponseEntity.ok(AuthContextResponse.from(user));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiErrorResponse.of("AUTHENTICATION_FAILED",
                            "The username or password is invalid.", request.getRequestURI()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CampusGuinnessUserDetails user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiErrorResponse.of("AUTHENTICATION_REQUIRED",
                            "Authentication is required.", "/api/v1/auth/me"));
        }
        return ResponseEntity.ok(AuthContextResponse.from(user));
    }
}
