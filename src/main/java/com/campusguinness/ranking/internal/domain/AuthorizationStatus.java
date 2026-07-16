package com.campusguinness.ranking.internal.domain;

/**
 * L3Authorization lifecycle states (ADR-005).
 * <pre>
 *   DRAFT → PENDING_REVIEW → APPROVED ⇄ SUSPENDED
 *     ↓         ↓               ↓          ↓
 *     └──→ WITHDRAWN ←─────────┴──────────┘
 *   PENDING_REVIEW → REJECTED → DRAFT
 * </pre>
 */
public enum AuthorizationStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    SUSPENDED,
    WITHDRAWN
}
