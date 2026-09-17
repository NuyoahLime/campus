package com.campusguinness.result.application.port;

import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;

import java.time.Instant;
import java.util.Optional;

public interface ResultVersionRepository {
    void create(ResultVersion resultVersion);
    Optional<ResultVersion> findById(ResultVersionId id);
    int nextVersionNumberFor(ActivityResultId resultId);
    void markPublishedInternally(ResultVersionId id, Instant publishedAt);
    void markPublishedPublicly(ResultVersionId id, Instant publishedAt);
}
