package com.campusguinness.interfaces.web.user;

import com.campusguinness.identity.application.result.UserResult;
import com.campusguinness.identity.application.service.UserApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserApplicationService service;

    public UserController(UserApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        UserResult r = service.create(req.username(), req.initialPassword());
        return ResponseEntity.created(URI.create("/api/v1/users/" + r.id()))
                .body(new UserResponse(r.id(), r.username(), r.status()));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activate(@PathVariable UUID id) {
        UserResult r = service.activate(id);
        return ResponseEntity.ok(new UserResponse(r.id(), r.username(), r.status()));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<UserResponse> disable(@PathVariable UUID id) {
        UserResult r = service.disable(id);
        return ResponseEntity.ok(new UserResponse(r.id(), r.username(), r.status()));
    }

    @PostMapping("/{id}/re-enable")
    public ResponseEntity<UserResponse> reEnable(@PathVariable UUID id) {
        UserResult r = service.reEnable(id);
        return ResponseEntity.ok(new UserResponse(r.id(), r.username(), r.status()));
    }
}
