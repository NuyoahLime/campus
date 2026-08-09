package com.campusguinness.interfaces.web.school;

import java.util.UUID;

public record PublicSchoolSummaryResponse(UUID id, String name, String schoolType, String region) {}
