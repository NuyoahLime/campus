package com.campusguinness.identity.application.query;

import java.util.List;

public record ReviewPageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements
) {
}
