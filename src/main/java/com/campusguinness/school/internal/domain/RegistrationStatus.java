package com.campusguinness.school.internal.domain;

/** SchoolRegistration state machine: DRAFT→SUBMITTED→(APPROVED|REJECTED|WITHDRAWN|NEED_SUPPLEMENT→SUBMITTED|WITHDRAWN) */
public enum RegistrationStatus {
    DRAFT, SUBMITTED, NEED_SUPPLEMENT, APPROVED, REJECTED, WITHDRAWN
}
