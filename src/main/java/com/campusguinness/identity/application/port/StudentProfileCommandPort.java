package com.campusguinness.identity.application.port;

import java.util.UUID;

public interface StudentProfileCommandPort {

    boolean existsByUserId(UUID userId);

    boolean existsByMembershipId(UUID membershipId);

    void create(CreateStudentProfileCommand command);
}
