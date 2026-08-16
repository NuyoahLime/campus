package com.campusguinness.interfaces.web.schooladmininvitation;

import com.campusguinness.identity.application.service.SchoolAdminInvitationManagementService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin-invitations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SchoolAdminInvitationController {

    private final SchoolAdminInvitationManagementService service;

    public SchoolAdminInvitationController(SchoolAdminInvitationManagementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SchoolAdminInvitationResponse> create(
            @Valid @RequestBody CreateSchoolAdminInvitationRequest request
    ) {
        var result = service.create(request.username(), request.schoolId(), request.expiresAt());
        return noStore(ResponseEntity.created(URI.create(
                "/api/v1/schools/" + result.schoolId()
                        + "/school-admin-invitations/" + result.invitationId()
        )))
                .body(SchoolAdminInvitationResponse.from(result));
    }

    @PostMapping("/{invitationId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID invitationId) {
        service.revoke(invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{invitationId}/regenerate")
    public ResponseEntity<SchoolAdminInvitationResponse> regenerate(@PathVariable UUID invitationId) {
        var result = service.regenerate(invitationId);
        return noStore(ResponseEntity.ok())
                .body(SchoolAdminInvitationResponse.from(result));
    }

    private <T> ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder.cacheControl(CacheControl.noStore()).header("Pragma", "no-cache");
    }
}
