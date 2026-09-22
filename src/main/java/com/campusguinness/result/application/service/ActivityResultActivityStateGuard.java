package com.campusguinness.result.application.service;

import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ExecutionStatus;

/** Execution-state policy shared by ordinary ActivityResult mutations. */
final class ActivityResultActivityStateGuard {
    private ActivityResultActivityStateGuard() {
    }

    static void requireOrdinaryMutationAllowed(Activity activity) {
        ExecutionStatus status = activity.executionStatus();
        if (status != ExecutionStatus.PUBLISHED
                && status != ExecutionStatus.IN_PROGRESS
                && status != ExecutionStatus.ENDED) {
            throw new IllegalStateException(
                    "Cannot mutate ActivityResult for activity execution status " + status);
        }
    }
}
