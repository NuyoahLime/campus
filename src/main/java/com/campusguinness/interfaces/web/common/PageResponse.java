package com.campusguinness.interfaces.web.common;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size > 0 ? (int) ((total + size - 1) / size) : 0;
        return new PageResponse<>(items, page, size, total, totalPages, page + 1 < totalPages);
    }
}
