package com.campusguinness.school.internal.domain;

/** School lifecycle: PENDING_ENABLE→NORMAL⇄SUSPENDED→DISABLED→PENDING_ENABLE */
public enum SchoolStatus {
    PENDING_ENABLE, NORMAL, SUSPENDED, DISABLED
}
