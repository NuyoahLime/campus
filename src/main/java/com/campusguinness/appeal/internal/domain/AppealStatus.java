package com.campusguinness.appeal.internal.domain;

/**
 * ScoreAppeal lifecycle states (13 states, most complex in the system).
 * <pre>
 *   SUBMITTED → PROCESSING → REJECTED (terminal)
 *                          → ACCEPTED_PENDING_CORRECTION → SCORE_CORRECTING → RESOLVED (terminal)
 *                          → RANK_CHECKING → RANK_FIXING → RESOLVED
 *                                         → REJECTED
 *                                         → WITHDRAWN
 *                          → ESCALATED → PLATFORM_PROCESSING → PLATFORM_DECIDED → RESOLVED
 *                                                             → RETURNED_TO_SCHOOL → PROCESSING
 *                          → WITHDRAWN (terminal)
 *   SUBMITTED → WITHDRAWN (terminal)
 * </pre>
 */
public enum AppealStatus {
    SUBMITTED,
    PROCESSING,
    REJECTED,
    ACCEPTED_PENDING_CORRECTION,
    SCORE_CORRECTING,
    RANK_CHECKING,
    RANK_FIXING,
    ESCALATED,
    PLATFORM_PROCESSING,
    RETURNED_TO_SCHOOL,
    PLATFORM_DECIDED,
    RESOLVED,
    WITHDRAWN
}
