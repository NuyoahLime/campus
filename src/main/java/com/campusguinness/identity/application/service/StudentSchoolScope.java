package com.campusguinness.identity.application.service;

import java.util.UUID;

public record StudentSchoolScope(UUID studentId, UUID schoolId) {
}
