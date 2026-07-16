package com.campusguinness.score.internal.domain;

/**
 * ScoreAttempt lifecycle states.
 *
 * <pre>
 *   DRAFT → PENDING_REVIEW → APPROVED → INVALIDATED (terminal)
 *     ↑         ↓
 *     └─ REJECTED ←┘
 * </pre>
 */
public enum AttemptStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    INVALIDATED
}
