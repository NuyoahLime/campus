package com.campusguinness.school.internal.persistence;

import com.campusguinness.school.application.query.SchoolOperationalQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
class SchoolOperationalQueryAdapter implements SchoolOperationalQuery {
    private final SchoolJpaRepository schools;
    SchoolOperationalQueryAdapter(SchoolJpaRepository schools) { this.schools = schools; }
    @Override @Transactional(readOnly = true)
    public boolean isNormal(UUID schoolId) {
        return schoolId != null && schools.existsByIdAndSchoolStatus(schoolId, "NORMAL");
    }
}
