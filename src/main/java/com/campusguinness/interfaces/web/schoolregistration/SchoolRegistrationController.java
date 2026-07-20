package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.school.application.command.SubmitSchoolRegistrationCommand;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.service.SchoolRegistrationApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-registrations")
public class SchoolRegistrationController {

    private final SchoolRegistrationApplicationService service;
    private final CurrentActor currentActor;
    private final SchoolMembershipResolver membershipResolver;
    private final JdbcTemplate jdbc;

    public SchoolRegistrationController(SchoolRegistrationApplicationService service,
                                         CurrentActor currentActor,
                                         SchoolMembershipResolver membershipResolver,
                                         JdbcTemplate jdbc) {
        this.service = service;
        this.currentActor = currentActor;
        this.membershipResolver = membershipResolver;
        this.jdbc = jdbc;
    }

    @PostMapping
    public ResponseEntity<SchoolRegistrationResponse> submit(@Valid @RequestBody SubmitSchoolRegistrationRequest req) {
        var cmd = new SubmitSchoolRegistrationCommand(
                req.schoolName(), req.unifiedCodeType(), req.unifiedCode(), req.schoolType(),
                req.region(), req.address(), req.contactName(), req.contactPhone(), req.contactEmail(),
                req.description(), req.evidenceFileKey());
        SchoolRegistrationResult r = service.submit(cmd);
        return ResponseEntity.created(URI.create("/api/v1/school-registrations/" + r.id()))
                .body(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolRegistrationResponse> approve(@PathVariable UUID id, @Valid @RequestBody ApproveSchoolRegistrationRequest req) {
        UUID actorId = currentActor.requireUserId();
        UUID realSchoolId = resolveRegistrationSchoolId(id);
        AuthorizationPolicy.requireTeacherOrAbove(membershipResolver, actorId, realSchoolId);
        SchoolRegistrationResult r = service.approve(id, actorId, req.comment(), realSchoolId);
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolRegistrationResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectSchoolRegistrationRequest req) {
        UUID actorId = currentActor.requireUserId();
        UUID realSchoolId = resolveRegistrationSchoolId(id);
        AuthorizationPolicy.requireTeacherOrAbove(membershipResolver, actorId, realSchoolId);
        SchoolRegistrationResult r = service.reject(id, actorId, req.reason());
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    /** Resolve the actual schoolId from the persisted registration — never trust the request body. */
    private UUID resolveRegistrationSchoolId(UUID registrationId) {
        var rows = jdbc.queryForList(
                "SELECT created_school_id FROM school_registrations WHERE id = ?", UUID.class, registrationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("SchoolRegistration not found: " + registrationId);
        }
        UUID schoolId = rows.getFirst();
        if (schoolId == null) {
            throw new IllegalArgumentException("SchoolRegistration has no associated school");
        }
        return schoolId;
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<SchoolRegistrationResponse> withdraw(@PathVariable UUID id) {
        SchoolRegistrationResult r = service.withdraw(id);
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }
}
