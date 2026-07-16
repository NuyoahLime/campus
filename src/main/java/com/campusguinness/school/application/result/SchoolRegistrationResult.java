package com.campusguinness.school.application.result;
import java.util.UUID;
public record SchoolRegistrationResult(UUID id, String schoolName, String status, UUID createdSchoolId) {}
