package com.campusguinness.media.internal.domain;

/**
 * Media public (platform) review lifecycle states.
 *
 * <pre>
 *   NOT_SUBMITTED → PENDING_PUBLIC_REVIEW → PLATFORM_APPROVED → PUBLIC
 *                                         → PLATFORM_REJECTED → NOT_SUBMITTED
 *                                         → PLATFORM_REJECTED → PENDING_PUBLIC_REVIEW
 *                                                       PUBLIC → PLATFORM_TAKEDOWN → NOT_SUBMITTED
 * </pre>
 */
public enum MediaPublicStatus {
    NOT_SUBMITTED,
    PENDING_PUBLIC_REVIEW,
    PLATFORM_APPROVED,
    PLATFORM_REJECTED,
    PUBLIC,
    PLATFORM_TAKEDOWN
}
