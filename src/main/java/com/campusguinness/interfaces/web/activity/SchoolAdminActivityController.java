package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/activities")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminActivityController {

    private final ActivityManagementService service;
    private final ActivityQueryService queryService;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort membershipPort;

    public SchoolAdminActivityController(ActivityManagementService service,
                                          ActivityQueryService queryService,
                                          CurrentActor currentActor,
                                          SchoolMembershipQueryPort membershipPort) {
        this.service = service;
        this.queryService = queryService;
        this.currentActor = currentActor;
        this.membershipPort = membershipPort;
    }

    private UUID requireSchoolId() {
        UUID userId = currentActor.requireUserId();
        return membershipPort.findActiveSchoolAdminSchoolId(userId)
                .orElseThrow(() -> new IllegalStateException("No active SCHOOL_ADMIN membership"));
    }

    private void requireOwnSchool(UUID activitySchoolId) {
        UUID mySchoolId = requireSchoolId();
        if (!mySchoolId.equals(activitySchoolId)) {
            throw new IllegalArgumentException("Activity not found");
        }
    }

    // ── List & Detail ──

    @GetMapping
    public ResponseEntity<PageResponse<ActivityListItem>> list(
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String publicStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID schoolId = requireSchoolId();
        var result = queryService.listBySchool(schoolId, executionStatus, publicStatus, keyword, page, size);
        var items = result.items().stream()
                .map(r -> new ActivityListItem(r.id(), r.schoolId(), r.title(),
                        r.startTime(), r.endTime(), r.location(), r.executionStatus(), r.publicStatus()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<ActivityDetailResponse> getDetail(@PathVariable UUID activityId) {
        var act = service.findById(activityId);
        requireOwnSchool(act.schoolId());

        var projects = service.listProjects(activityId).stream()
                .map(p -> new ActivityProjectResponse(p.id(), p.activityId(), p.projectId()))
                .toList();
        var teachers = projects.stream()
                .flatMap(p -> service.listResponsibleTeachers(activityId, p.projectId()).stream()
                        .map(t -> new ResponsibleTeacherResponse(t.id(), t.activityProjectId(), t.teacherMembershipId(), t.userId(), t.username(), t.subject(), t.title(), t.membershipStatus(), t.accountStatus())))
                .toList();

        return ResponseEntity.ok(new ActivityDetailResponse(
                act.id().value(), act.schoolId(), act.title(), act.description(),
                act.startTime(), act.endTime(), act.location(),
                act.executionStatus().name(), act.publicStatus().name(), act.createdBy(),
                projects, teachers));
    }

    // ── Create ──

    @PostMapping
    public ResponseEntity<ActivityResponse> create(@Valid @RequestBody CreateActivityRequest req) {
        UUID schoolId = requireSchoolId();
        UUID createdBy = currentActor.requireUserId();
        var cmd = new CreateActivityCommand(schoolId, createdBy, req.title(),
                req.description(), req.startTime(), req.endTime(), req.location());
        ActivityResult r = service.create(cmd);
        return ResponseEntity.created(URI.create("/api/v1/school-admin/activities/" + r.id()))
                .body(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    // ── Update DRAFT ──

    @PatchMapping("/{activityId}")
    public ResponseEntity<ActivityResponse> update(@PathVariable UUID activityId,
                                                    @RequestBody UpdateActivityRequest req) {
        requireOwnSchool(service.findById(activityId).schoolId());
        if (req.isEmpty()) throw new IllegalArgumentException("At least one field required");
        ActivityResult r = service.update(activityId, req.title(), req.description(),
                req.startTime(), req.endTime(), req.location());
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    // ── Lifecycle ──

    @PostMapping("/{id}/publish")
    public ResponseEntity<ActivityResponse> publish(@PathVariable UUID id) {
        requireOwnSchool(service.findById(id).schoolId());
        ActivityResult r = service.publish(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ActivityResponse> beginExecution(@PathVariable UUID id) {
        requireOwnSchool(service.findById(id).schoolId());
        ActivityResult r = service.beginExecution(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<ActivityResponse> finish(@PathVariable UUID id) {
        requireOwnSchool(service.findById(id).schoolId());
        ActivityResult r = service.finish(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ActivityResponse> cancel(@PathVariable UUID id) {
        requireOwnSchool(service.findById(id).schoolId());
        ActivityResult r = service.cancel(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    // ── Public Review ──

    @PostMapping("/{id}/submit-public-review")
    public ResponseEntity<ActivityResponse> submitPublicReview(@PathVariable UUID id) {
        requireOwnSchool(service.findById(id).schoolId());
        ActivityResult r = service.submitForPublicReview(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/withdraw-public")
    public ResponseEntity<ActivityResponse> withdrawPublic(@PathVariable UUID id) {
        requireOwnSchool(service.findById(id).schoolId());
        ActivityResult r = service.withdrawPublic(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    // ── Projects ──

    @GetMapping("/{activityId}/projects")
    public List<ActivityProjectResponse> listProjects(@PathVariable UUID activityId) {
        requireOwnSchool(service.findById(activityId).schoolId());
        return service.listProjects(activityId).stream()
                .map(p -> new ActivityProjectResponse(p.id(), p.activityId(), p.projectId()))
                .toList();
    }

    @PostMapping("/{activityId}/projects")
    public ResponseEntity<ActivityProjectResponse> addProject(@PathVariable UUID activityId,
                                                               @RequestBody AddActivityProjectRequest req) {
        requireOwnSchool(service.findById(activityId).schoolId());
        var p = service.addProject(activityId, req.projectId());
        return ResponseEntity.ok(new ActivityProjectResponse(p.id(), p.activityId(), p.projectId()));
    }

    @DeleteMapping("/{activityId}/projects/{projectId}")
    public ResponseEntity<Void> removeProject(@PathVariable UUID activityId,
                                               @PathVariable UUID projectId) {
        requireOwnSchool(service.findById(activityId).schoolId());
        service.removeProject(activityId, projectId);
        return ResponseEntity.noContent().build();
    }

    // ── Responsible Teachers ──

    @GetMapping("/{activityId}/projects/{projectId}/responsible-teachers")
    public List<ResponsibleTeacherResponse> listResponsibleTeachers(@PathVariable UUID activityId,
                                                                     @PathVariable UUID projectId) {
        requireOwnSchool(service.findById(activityId).schoolId());
        return service.listResponsibleTeachers(activityId, projectId).stream()
                .map(r -> new ResponsibleTeacherResponse(r.id(), r.activityProjectId(), r.teacherMembershipId(), r.userId(), r.username(), r.subject(), r.title(), r.membershipStatus(), r.accountStatus()))
                .toList();
    }

    @PostMapping("/{activityId}/projects/{projectId}/responsible-teachers")
    public ResponseEntity<ResponsibleTeacherResponse> assignResponsibleTeacher(
            @PathVariable UUID activityId,
            @PathVariable UUID projectId,
            @RequestBody AssignResponsibleTeacherRequest req) {
        requireOwnSchool(service.findById(activityId).schoolId());
        var r = service.assignResponsibleTeacher(activityId, projectId, req.teacherId());
        return ResponseEntity.ok(new ResponsibleTeacherResponse(r.id(), r.activityProjectId(), r.teacherMembershipId(), r.userId(), r.username(), r.subject(), r.title(), r.membershipStatus(), r.accountStatus()));
    }

    @DeleteMapping("/{activityId}/projects/{projectId}/responsible-teachers/{teacherId}")
    public ResponseEntity<Void> unassignResponsibleTeacher(@PathVariable UUID activityId,
                                                            @PathVariable UUID projectId,
                                                            @PathVariable UUID teacherId) {
        requireOwnSchool(service.findById(activityId).schoolId());
        service.unassignResponsibleTeacher(activityId, projectId, teacherId);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ──

    public record UpdateActivityRequest(String title, String description,
            java.time.Instant startTime, java.time.Instant endTime, String location) {
        public boolean isEmpty() {
            return title == null && description == null && startTime == null
                    && endTime == null && location == null;
        }
    }

    public record ActivityDetailResponse(UUID activityId, UUID schoolId, String title,
            String description, java.time.Instant startTime, java.time.Instant endTime,
            String location, String executionStatus, String publicStatus, UUID createdBy,
            List<ActivityProjectResponse> projects,
            List<ResponsibleTeacherResponse> responsibleTeachers) {}
}
