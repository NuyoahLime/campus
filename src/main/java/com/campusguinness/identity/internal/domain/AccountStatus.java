package com.campusguinness.identity.internal.domain;

/**
 * User account lifecycle states (4 states).
 * <pre>
 *   PENDING_ACTIVATION → NORMAL ⇄ LOCKED
 *          ↓               ↓        ↓
 *        DISABLED ←────────┴────────┘
 *          ↓
 *        NORMAL
 * </pre>
 */
public enum AccountStatus {
    PENDING_ACTIVATION, NORMAL, LOCKED, DISABLED
}
