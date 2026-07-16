package com.campusguinness.project.internal.domain;

/**
 * Business category of a challenge project (e.g. ATHLETICS, SPEED, ACADEMIC).
 * V1: free-form category string, validated by length.
 */
public record ProjectCategory(String value) {
    private static final int MAX_LENGTH = 64;

    public ProjectCategory {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Project category must not be null or blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Project category exceeds max length " + MAX_LENGTH);
        }
    }
}
