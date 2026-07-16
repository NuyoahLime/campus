package com.campusguinness.media.internal.domain;

/**
 * Media internal (school-level) lifecycle states.
 *
 * <pre>
 *   DRAFT → PENDING_INTERNAL_REVIEW → INTERNAL_APPROVED ⇄ INTERNAL_DISABLED
 *             ↓
 *        INTERNAL_REJECTED → DRAFT
 * </pre>
 */
public enum MediaInternalStatus {
    DRAFT,
    PENDING_INTERNAL_REVIEW,
    INTERNAL_APPROVED,
    INTERNAL_REJECTED,
    INTERNAL_DISABLED
}
