package com.campusguinness.interfaces.web.rankingmanagement;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.ranking.application.service.RankingManagementQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RankingManagementController {
    private final RankingManagementQueryService service;

    public RankingManagementController(RankingManagementQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/school-admin/ranking-definitions")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<PageResponse<RankingManagementResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.list(page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items().stream().map(RankingManagementResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements()));
    }

    @GetMapping("/api/v1/school-admin/ranking-definitions/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<RankingManagementResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(RankingManagementResponse.from(service.detail(id)));
    }
}
