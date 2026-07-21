package com.campusguinness.interfaces.web.schoolregistration;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.school.application.command.SubmitSchoolRegistrationCommand;
import com.campusguinness.school.application.result.SchoolRegistrationResult;
import com.campusguinness.school.application.service.SchoolRegistrationApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-registrations")
public class SchoolRegistrationController {

    private final SchoolRegistrationApplicationService service;
    private final CurrentActor currentActor;
    private final JdbcTemplate jdbc;

    public SchoolRegistrationController(SchoolRegistrationApplicationService service,
                                         CurrentActor currentActor, JdbcTemplate jdbc) {
        this.service = service;
        this.currentActor = currentActor;
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public List<SchoolRegistrationSummary> listPending() {
        return jdbc.query(
                "SELECT id, school_name, school_type, region, registration_status, created_at " +
                        "FROM school_registrations WHERE registration_status = 'SUBMITTED' " +
                        "ORDER BY created_at DESC",
                (rs, rowNum) -> new SchoolRegistrationSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("school_name"),
                        rs.getString("school_type"),
                        rs.getString("region"),
                        rs.getString("registration_status"),
                        rs.getObject("created_at", Instant.class)));
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
        SchoolRegistrationResult r = service.approve(id, currentActor.requireUserId(), req.comment(), req.schoolId());
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<SchoolRegistrationResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectSchoolRegistrationRequest req) {
        SchoolRegistrationResult r = service.reject(id, currentActor.requireUserId(), req.reason());
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<SchoolRegistrationResponse> withdraw(@PathVariable UUID id) {
        SchoolRegistrationResult r = service.withdraw(id);
        return ResponseEntity.ok(new SchoolRegistrationResponse(r.id(), r.schoolName(), r.status(), r.createdSchoolId()));
    }
}
