package com.campusguinness.interfaces.web.user;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * General user endpoints are restricted to SUPER_ADMIN with audit context required.
 * Use dedicated provisioning endpoints for daily account creation:
 * - SUPER_ADMIN: POST /api/v1/admin/schools/{schoolId}/administrators
 * - SCHOOL_ADMIN: POST /api/v1/school-admin/accounts
 * - Activation: POST /api/v1/auth/activate (public, with CSRF)
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody Object req) {
        return ResponseEntity.status(403).body("Use dedicated admin/school-admin provisioning endpoints");
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        return ResponseEntity.status(403).body("Use POST /api/v1/auth/activate for account activation");
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> disable(@PathVariable UUID id) {
        return ResponseEntity.status(403).build();
    }

    @PostMapping("/{id}/re-enable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> reEnable(@PathVariable UUID id) {
        return ResponseEntity.status(403).build();
    }
}
