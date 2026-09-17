package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewAction;
import com.campusguinness.result.internal.domain.ResultReviewRecord;
import com.campusguinness.result.internal.domain.ResultReviewRecordId;
import com.campusguinness.result.internal.domain.ResultVersionId;

final class ResultReviewRecordPersistenceMapper {
    private ResultReviewRecordPersistenceMapper() {}

    static ResultReviewRecordEntity toEntity(ResultReviewRecord record) {
        var entity = new ResultReviewRecordEntity();
        entity.setId(record.id().value());
        entity.setResultId(record.resultId().value());
        entity.setResultVersionId(record.resultVersionId().value());
        entity.setAction(record.action().name());
        entity.setSubmittedBy(record.submittedBy());
        entity.setSubmittedAt(record.submittedAt());
        entity.setReviewerId(record.reviewerId());
        entity.setReviewedAt(record.reviewedAt());
        entity.setReason(record.reason());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    static ResultReviewRecord toDomain(ResultReviewRecordEntity entity) {
        return ResultReviewRecord.reconstitute(
                new ResultReviewRecordId(entity.getId()),
                new ActivityResultId(entity.getResultId()),
                new ResultVersionId(entity.getResultVersionId()),
                ResultReviewAction.valueOf(entity.getAction()),
                entity.getSubmittedBy(),
                entity.getSubmittedAt(),
                entity.getReviewerId(),
                entity.getReviewedAt(),
                entity.getReason(),
                entity.getCreatedAt());
    }
}
