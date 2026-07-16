package com.campusguinness.interfaces.web.schoolregistration;

import java.util.UUID;

public record SchoolRegistrationResponse(UUID id, String schoolName, String status, UUID createdSchoolId) {}
