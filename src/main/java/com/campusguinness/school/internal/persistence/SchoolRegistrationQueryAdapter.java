package com.campusguinness.school.internal.persistence;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolRegistrationDetailResult;
import com.campusguinness.school.application.query.model.SchoolRegistrationListResult;
import com.campusguinness.school.application.query.port.SchoolRegistrationQueryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
class SchoolRegistrationQueryAdapter implements SchoolRegistrationQueryPort {

    private final SchoolRegistrationJpaRepository jpa;

    SchoolRegistrationQueryAdapter(SchoolRegistrationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public QueryPage<SchoolRegistrationListResult> findAll(String status, int page, int size) {
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending().and(Sort.by("id").descending())
        );
        var result = status == null
                ? jpa.findAll(pageable)
                : jpa.findByRegistrationStatus(status, pageable);
        List<SchoolRegistrationListResult> items = result.getContent().stream()
                .map(this::listResult)
                .toList();
        return new QueryPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public Optional<SchoolRegistrationDetailResult> findById(UUID id) {
        return jpa.findById(id).map(this::detailResult);
    }

    private SchoolRegistrationListResult listResult(SchoolRegistrationEntity entity) {
        return new SchoolRegistrationListResult(
                entity.getId(), entity.getSchoolName(), entity.getSchoolType(), entity.getRegion(),
                entity.getContactName(), entity.getRegistrationStatus(), entity.getCreatedAt()
        );
    }

    private SchoolRegistrationDetailResult detailResult(SchoolRegistrationEntity entity) {
        String evidenceFileKey = entity.getEvidenceFileKey();
        return new SchoolRegistrationDetailResult(
                entity.getId(), entity.getSchoolName(), entity.getUnifiedCodeType(), entity.getUnifiedCode(),
                entity.getSchoolType(), entity.getRegion(), entity.getAddress(), entity.getContactName(),
                entity.getContactPhone(), entity.getContactEmail(), entity.getDescription(),
                evidenceFileKey != null && !evidenceFileKey.isBlank(), entity.getRegistrationStatus(),
                entity.getCreatedSchoolId(), entity.getReviewedBy(), entity.getReviewedAt(),
                entity.getReviewComment(), entity.getRejectReason(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
