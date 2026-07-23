package com.campusguinness.project.application.query.model;

public record PublicProjectListFilter(
        String keyword,
        String category,
        String scoreStorageType,
        String venueKeyword,
        String equipmentKeyword) {}
