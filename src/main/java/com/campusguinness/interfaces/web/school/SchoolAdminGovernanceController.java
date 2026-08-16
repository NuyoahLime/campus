package com.campusguinness.interfaces.web.school;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.school.application.query.SchoolAdminGovernanceQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schools/{schoolId}")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SchoolAdminGovernanceController {

    private final SchoolAdminGovernanceQueryService queryService;

    public SchoolAdminGovernanceController(SchoolAdminGovernanceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/school-admins")
    public ResponseEntity<List<SchoolAdminAccountResponse>> listSchoolAdmins(@PathVariable UUID schoolId) {
        var items = queryService.listSchoolAdmins(schoolId).stream()
                .map(SchoolAdminAccountResponse::from)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/school-admin-invitations")
    public ResponseEntity<PageResponse<SchoolAdminInvitationQueryResponse>> listInvitations(
            @PathVariable UUID schoolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        var result = queryService.listInvitations(schoolId, page, size, status);
        var items = result.items().stream().map(SchoolAdminInvitationQueryResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/school-admin-invitations/{invitationId}")
    public ResponseEntity<SchoolAdminInvitationQueryResponse> invitationDetail(
            @PathVariable UUID schoolId,
            @PathVariable UUID invitationId
    ) {
        return ResponseEntity.ok(SchoolAdminInvitationQueryResponse.from(
                queryService.invitationDetail(schoolId, invitationId)
        ));
    }
}
