package com.campusguinness.project.internal.domain;

/** Project lifecycle status. State machine: DRAFT → PUBLISHED → ARCHIVED → PUBLISHED */
public enum ProjectStatus {
    DRAFT, PUBLISHED, ARCHIVED
}
