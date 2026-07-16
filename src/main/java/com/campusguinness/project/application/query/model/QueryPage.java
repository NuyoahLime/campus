package com.campusguinness.project.application.query.model;

import java.util.List;

public record QueryPage<T>(List<T> items, int page, int size, long totalElements) {}
