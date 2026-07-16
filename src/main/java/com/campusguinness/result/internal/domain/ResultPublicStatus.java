package com.campusguinness.result.internal.domain;

/**
 * ActivityResult public review lifecycle states.
 *
 * <pre>
 *   NOT_SUBMITTED → PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED → PUBLIC
 *                                         → PLATFORM_REJECTED → NOT_SUBMITTED
 *                                                       PUBLIC → ANOMALY_PENDING → PUBLIC
 *                                                              → PLATFORM_TAKEDOWN → NOT_SUBMITTED
 *                                              ANOMALY_PENDING → NOT_SUBMITTED
 *                                              ANOMALY_PENDING → PLATFORM_TAKEDOWN
 * </pre>
 */
public enum ResultPublicStatus {
    NOT_SUBMITTED,
    PENDING_PUBLIC_REVIEW,
    PLATFORM_APPROVED,
    PLATFORM_REJECTED,
    PUBLIC,
    ANOMALY_PENDING,
    PLATFORM_TAKEDOWN
}
