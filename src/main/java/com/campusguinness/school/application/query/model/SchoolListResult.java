package com.campusguinness.school.application.query.model;

import java.util.UUID;

public record SchoolListResult(UUID id, String name, String schoolType, String region) {}
