package com.campusguinness.school.application.port;

import com.campusguinness.school.internal.domain.School;
import com.campusguinness.school.internal.domain.SchoolId;
import java.util.Optional;

public interface SchoolRepository {
    void save(School school);
    Optional<School> findById(SchoolId id);
    Optional<School> findByIdForUpdate(SchoolId id);
    boolean existsByUnifiedCode(String unifiedCodeType, String unifiedCode);
}
