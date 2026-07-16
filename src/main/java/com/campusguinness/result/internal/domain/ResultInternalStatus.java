package com.campusguinness.result.internal.domain;

/**
 * ActivityResult internal lifecycle states.
 *
 * <pre>
 *   DRAFT → INTERNAL_PUBLISHED → INTERNAL_WITHDRAWN → DRAFT
 * </pre>
 */
public enum ResultInternalStatus {
    DRAFT,
    INTERNAL_PUBLISHED,
    INTERNAL_WITHDRAWN
}
