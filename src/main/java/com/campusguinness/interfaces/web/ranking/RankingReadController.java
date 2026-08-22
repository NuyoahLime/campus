package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.ranking.application.service.RankingReadQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RankingReadController {
    private final RankingReadQueryService service;

    public RankingReadController(RankingReadQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/public/rankings")
    public ResponseEntity<PageResponse<RankingReadResponse.Summary>> publicList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(page(service.listPublic(page, size)));
    }

    @GetMapping("/api/v1/public/rankings/{id}")
    public ResponseEntity<RankingReadResponse> publicDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(RankingReadResponse.from(service.publicDetail(id)));
    }

    @GetMapping("/api/v1/student/rankings")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<PageResponse<RankingReadResponse.Summary>> studentList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(page(service.listStudent(page, size)));
    }

    @GetMapping("/api/v1/student/rankings/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<RankingReadResponse> studentDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(RankingReadResponse.from(service.studentDetail(id)));
    }

    @GetMapping("/api/v1/school-admin/rankings")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<PageResponse<RankingReadResponse.Summary>> schoolAdminList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(page(service.listSchoolAdmin(page, size)));
    }

    @GetMapping("/api/v1/school-admin/rankings/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<RankingReadResponse> schoolAdminDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(RankingReadResponse.from(service.schoolAdminDetail(id)));
    }

    private PageResponse<RankingReadResponse.Summary> page(
            com.campusguinness.project.application.query.model.QueryPage<
                    com.campusguinness.ranking.application.query.model.RankingReadSummaryResult> result) {
        var items = result.items()
                .stream().map(RankingReadResponse.Summary::from).toList();
        return PageResponse.of(items, result.page(), result.size(), result.totalElements());
    }
}
