package com.campusguinness.interfaces.web.activity;

import com.campusguinness.identity.application.query.SchoolTeacherDirectoryQueryService;
import com.campusguinness.identity.application.query.port.SchoolTeacherDirectoryQueryPort;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/teachers")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminTeacherController {

    private final SchoolTeacherDirectoryQueryService queryService;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort membershipPort;

    public SchoolAdminTeacherController(SchoolTeacherDirectoryQueryService queryService,
                                         CurrentActor currentActor,
                                         SchoolMembershipQueryPort membershipPort) {
        this.queryService = queryService;
        this.currentActor = currentActor;
        this.membershipPort = membershipPort;
    }

    private UUID requireSchoolId() {
        UUID userId = currentActor.requireUserId();
        return membershipPort.findActiveSchoolAdminSchoolId(userId)
                .orElseThrow(() -> new IllegalStateException("No active SCHOOL_ADMIN membership"));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TeacherDirectoryItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID schoolId = requireSchoolId();
        var result = queryService.listTeachers(schoolId, keyword, page, size);
        var items = result.items().stream()
                .map(r -> new TeacherDirectoryItem(r.userId(), r.membershipId(), r.username(), r.subject(), r.title()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    public record TeacherDirectoryItem(UUID userId, UUID membershipId, String username, String subject, String title) {}
}
