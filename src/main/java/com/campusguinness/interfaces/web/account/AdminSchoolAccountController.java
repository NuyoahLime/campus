package com.campusguinness.interfaces.web.account;

import com.campusguinness.infrastructure.security.AccountProvisioningService;
import com.campusguinness.infrastructure.security.CurrentActor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class AdminSchoolAccountController {

    private final AccountProvisioningService service;
    private final CurrentActor currentActor;

    public AdminSchoolAccountController(AccountProvisioningService service, CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @PostMapping("/api/v1/admin/schools/{schoolId}/administrators")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String,Object>> createSchoolAdmin(@PathVariable UUID schoolId, @Valid @RequestBody CreateRequest req) {
        var r = service.createSchoolAdmin(currentActor.requireUserId(), schoolId, req.username(), req.temporaryPassword());
        return ResponseEntity.ok(Map.of("userId", r.userId(), "username", r.username(), "role", r.role(), "schoolId", r.schoolId(), "schoolName", r.schoolName(), "accountStatus", r.accountStatus()));
    }

    @GetMapping("/api/v1/admin/schools/{schoolId}/administrators")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Map<String,Object>> listSchoolAdmins(@PathVariable UUID schoolId) {
        return service.listSchoolAdmins(schoolId, 0, 100).stream().map(a -> Map.<String,Object>of(
                "userId", a.userId(), "username", a.username(), "role", a.role(),
                "schoolName", a.schoolName(), "accountStatus", a.accountStatus(), "createdAt", a.createdAt())).toList();
    }

    public record CreateRequest(@NotBlank @Size(max=100) String username, @NotBlank String temporaryPassword) {}
}
