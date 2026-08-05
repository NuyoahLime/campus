package com.campusguinness.school.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolListResult;
import com.campusguinness.school.application.query.port.SchoolQueryPort;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolQueryAdapter implements SchoolQueryPort {

    private final SchoolJpaRepository jpa;
    SchoolQueryAdapter(SchoolJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public QueryPage<SchoolListResult> findNormal(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id").ascending()));
        var result = jpa.findBySchoolStatus("NORMAL", pageable);
        List<SchoolListResult> items = result.getContent().stream()
                .map(e -> new SchoolListResult(e.getId(), e.getName(), e.getSchoolType(), e.getRegion()))
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public boolean isEligibleForMembership(UUID schoolId) {
        return schoolId != null && jpa.existsByIdAndSchoolStatus(schoolId, "NORMAL");
    }
}
