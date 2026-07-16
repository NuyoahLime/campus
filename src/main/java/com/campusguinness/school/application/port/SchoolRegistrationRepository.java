package com.campusguinness.school.application.port;

import com.campusguinness.school.internal.domain.SchoolRegistration;
import com.campusguinness.school.internal.domain.SchoolRegistrationId;
import java.util.Optional;

public interface SchoolRegistrationRepository {
    void save(SchoolRegistration registration);
    Optional<SchoolRegistration> findById(SchoolRegistrationId id);
}
