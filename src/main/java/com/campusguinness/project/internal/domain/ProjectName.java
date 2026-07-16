package com.campusguinness.project.internal.domain;

/**
 * Business-constrained name for a challenge project.
 * Max 200 characters, non-blank.
 */
public record ProjectName(String value) {
    private static final int MAX_LENGTH = 200;

    public ProjectName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Project name must not be null or blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Project name exceeds max length " + MAX_LENGTH + ": " + value.length());
        }
    }
}
