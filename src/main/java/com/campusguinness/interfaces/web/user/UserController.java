package com.campusguinness.interfaces.web.user;

import com.campusguinness.identity.application.service.UserApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User lifecycle management — restricted to SUPER_ADMIN.
 * <p>
 * Ordinary users (STUDENT, TEACHER, SCHOOL_ADMIN) can read their own profile
 * via /auth/me. All mutating operations here require SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserApplicationService service;

    public UserController(UserApplicationService service) {
        this.service = service;
    }

    /**
     * Create a new ordinary user with an initial password.
     * The user is created in PENDING_ACTIVATION state.
     */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        var result = service.create(req.username(), req.initialPassword());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(result.id(), result.username(), result.status()));
    }

    /**
     * Activate a user (PENDING_ACTIVATION → NORMAL).
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activate(@PathVariable UUID id) {
        var result = service.activate(id);
        return ResponseEntity.ok(new UserResponse(result.id(), result.username(), result.status()));
    }

    /**
     * Disable a user.
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<UserResponse> disable(@PathVariable UUID id) {
        var result = service.disable(id);
        return ResponseEntity.ok(new UserResponse(result.id(), result.username(), result.status()));
    }

    /**
     * Re-enable a previously disabled user.
     */
    @PostMapping("/{id}/re-enable")
    public ResponseEntity<UserResponse> reEnable(@PathVariable UUID id) {
        var result = service.reEnable(id);
        return ResponseEntity.ok(new UserResponse(result.id(), result.username(), result.status()));
    }
}
