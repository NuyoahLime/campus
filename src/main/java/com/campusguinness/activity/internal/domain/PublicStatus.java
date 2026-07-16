package com.campusguinness.activity.internal.domain;

/**
 * Activity public review lifecycle states.
 *
 * <pre>
 *   NOT_SUBMITTED → PENDING_PLATFORM_REVIEW → PLATFORM_APPROVED → PUBLIC
 *                                           → PLATFORM_REJECTED → NOT_SUBMITTED
 *                                                         PUBLIC → SCHOOL_WITHDRAWN → NOT_SUBMITTED
 *                                                         PUBLIC → PLATFORM_TAKEDOWN → NOT_SUBMITTED
 * </pre>
 */
public enum PublicStatus {
    NOT_SUBMITTED,
    PENDING_PLATFORM_REVIEW,
    PLATFORM_APPROVED,
    PLATFORM_REJECTED,
    PUBLIC,
    SCHOOL_WITHDRAWN,
    PLATFORM_TAKEDOWN
}
