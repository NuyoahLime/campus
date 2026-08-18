package com.campusguinness.school.application.query;

import java.util.UUID;

public interface SchoolOperationalQuery {
    boolean isNormal(UUID schoolId);
}
