package com.campusguinness.result.application.port;

import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;

import java.util.Optional;

public interface ResultVersionRepository {
    void create(ResultVersion resultVersion);
    Optional<ResultVersion> findById(ResultVersionId id);
}
