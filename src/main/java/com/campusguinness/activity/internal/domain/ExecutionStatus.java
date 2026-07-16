package com.campusguinness.activity.internal.domain;

/**
 * Activity execution lifecycle states.
 *
 * <pre>
 *   DRAFT → PUBLISHED → IN_PROGRESS → ENDED (terminal)
 *     |         |
 *     └──→ CANCELLED (terminal) ←────┘
 * </pre>
 */
public enum ExecutionStatus {
    DRAFT,
    PUBLISHED,
    IN_PROGRESS,
    ENDED,
    CANCELLED
}
