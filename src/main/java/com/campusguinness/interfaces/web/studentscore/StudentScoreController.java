package com.campusguinness.interfaces.web.studentscore;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.score.application.service.StudentScoreQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/scores")
@PreAuthorize("hasRole('STUDENT')")
public class StudentScoreController {
    private final StudentScoreQueryService service;

    public StudentScoreController(StudentScoreQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentScoreResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.list(page, size);
        var items = result.items().stream().map(StudentScoreResponse::from).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentScoreDetailResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentScoreDetailResponse.from(service.detail(id)));
    }
}
