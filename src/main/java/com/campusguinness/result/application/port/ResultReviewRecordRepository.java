package com.campusguinness.result.application.port;

import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultReviewRecord;
import com.campusguinness.result.internal.domain.ResultVersionId;

import java.util.List;

public interface ResultReviewRecordRepository {
    void append(ResultReviewRecord record);
    List<ResultReviewRecord> findByResult(ActivityResultId resultId);
    List<ResultReviewRecord> findByResultAndVersion(ActivityResultId resultId, ResultVersionId versionId);
}
