package com.campusguinness.identity.application.port;

import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;

import java.util.Optional;

public interface StudentIdentityApplicationRepository {
    void save(StudentIdentityApplication application);
    Optional<StudentIdentityApplication> findById(StudentIdentityApplicationId id);
}
