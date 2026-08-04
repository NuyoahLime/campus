package com.campusguinness.interfaces.web.account;

import com.campusguinness.infrastructure.security.AccountProvisioningService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/school-admin")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminAccountController {

    private final AccountProvisioningService service;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort membershipPort;

    public SchoolAdminAccountController(AccountProvisioningService service, CurrentActor currentActor, SchoolMembershipQueryPort membershipPort) {
        this.service = service;
        this.currentActor = currentActor;
        this.membershipPort = membershipPort;
    }

    private UUID actorSchoolId() {
        UUID userId = currentActor.requireUserId();
        return membershipPort.findActiveSchoolAdminSchoolId(userId)
                .orElseThrow(() -> new IllegalStateException("No active SCHOOL_ADMIN membership"));
    }

    @PostMapping("/accounts")
    public ResponseEntity<Map<String,Object>> createAccount(@Valid @RequestBody CreateAccountRequest req) {
        UUID schoolId = actorSchoolId();
        var r = service.createTeacherOrStudent(currentActor.requireUserId(), schoolId, req.username(), req.role());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(Map.of("userId", r.userId(), "username", r.username(), "role", r.role(), "schoolId", r.schoolId(), "schoolName", r.schoolName(), "accountStatus", r.accountStatus(), "temporaryPassword", r.temporaryPassword()));
    }

    @GetMapping("/accounts")
    public List<Map<String,Object>> listAccounts(@RequestParam(required=false) String role, @RequestParam(required=false) String status, @RequestParam(required=false) String keyword, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.listSchoolAccounts(actorSchoolId(), role, status, keyword, page, size).stream().map(a -> Map.<String,Object>of(
                "userId", a.userId(), "username", a.username(), "role", a.role(),
                "schoolName", a.schoolName(), "accountStatus", a.accountStatus(), "createdAt", a.createdAt())).toList();
    }

    public record CreateAccountRequest(@NotBlank @Size(max=100) String username, @NotBlank String role) {}
}
