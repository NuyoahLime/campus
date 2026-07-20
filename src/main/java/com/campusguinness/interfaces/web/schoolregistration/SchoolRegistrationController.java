package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.infrastructure.security.AuthorizationPolicy;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.infrastructure.security.SchoolMembershipResolver;
import com.campusguinness.school.application.command.SubmitSchoolRegistrationCommand;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.service.SchoolRegistrationApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    public SchoolRegistrationController(SchoolRegistrationApplicationService service,
                                         CurrentActor currentActor,
                                         SchoolMembershipResolver membershipResolver) {
        this.service = service;
        this.currentActor = currentActor;
        this.membershipResolver = membershipResolver;
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
        AuthorizationPolicy.requireTeacherOrAbove(membershipResolver, actorId, req.schoolId());
        SchoolRegistrationResult r = service.approve(id, actorId, req.comment(), req.schoolId());
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolRegistrationResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectSchoolRegistrationRequest req) {
        UUID actorId = currentActor.requireUserId();
        SchoolRegistrationResult r = service.reject(id, actorId, req.reason());
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<SchoolRegistrationResponse> withdraw(@PathVariable UUID id) {
        SchoolRegistrationResult r = service.withdraw(id);
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }
}
