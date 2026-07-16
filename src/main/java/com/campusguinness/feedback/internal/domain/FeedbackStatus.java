package com.campusguinness.feedback.internal.domain;

/**
 * Feedback lifecycle states (5 states).
 * <pre>
 *   SUBMITTED → PROCESSING → RESOLVED → CLOSED (terminal)
 *             → CLOSED (terminal)
 *   PROCESSING → ESCALATED → PROCESSING (cycle back)
 *              → CLOSED (terminal)
 * </pre>
 */
public enum FeedbackStatus {
    SUBMITTED, PROCESSING, RESOLVED, ESCALATED, CLOSED
}
