package com.campusguinness.score.internal.domain;

/**
 * Score storage type discriminator.
 * Mirrors the project module's ScoreStorageType to avoid cross-module internal-package dependency.
 */
public enum ScoreStorageType {
    INTEGER,
    DECIMAL,
    DURATION,
    GRADE
}
