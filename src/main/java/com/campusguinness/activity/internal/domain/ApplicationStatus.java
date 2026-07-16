package com.campusguinness.activity.internal.domain;

/**
 * ActivityApplication lifecycle states.
 *
 * <pre>
 *   DRAFT → SUBMITTED → APPROVED (terminal)
 *                    → REJECTED → DRAFT
 *                    → WITHDRAWN (terminal)
 * </pre>
 */
public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
