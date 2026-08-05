package com.campusguinness.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

public final class StudentIdentityApplication {

    private final StudentIdentityApplicationId id;
    private final UUID userId;
    private final UUID schoolId;
    private final String realName;
    private final String studentNumber;
    private final String grade;
    private final String className;
    private final String evidenceFileKey;
    private StudentIdentityApplicationStatus status;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;

    private StudentIdentityApplication(Builder b) {
        this.id = b.id;
        this.userId = b.userId;
        this.schoolId = b.schoolId;
        this.realName = b.realName;
        this.studentNumber = b.studentNumber;
        this.grade = b.grade;
        this.className = b.className;
        this.evidenceFileKey = b.evidenceFileKey;
        this.status = b.status != null ? b.status : StudentIdentityApplicationStatus.PENDING;
        this.reviewedBy = b.reviewedBy;
        this.reviewedAt = b.reviewedAt;
        this.rejectionReason = b.rejectionReason;
    }

    public static StudentIdentityApplication create(Builder builder) {
        validate(builder);
        return new StudentIdentityApplication(builder);
    }

    public static StudentIdentityApplication reconstitute(Builder builder) {
        validate(builder);
        if (builder.status == null) {
            throw new IllegalArgumentException("status required for reconstitute");
        }
        return new StudentIdentityApplication(builder);
    }

    public void approve(UUID reviewerId, Instant reviewedAt) {
        if (status != StudentIdentityApplicationStatus.PENDING) {
            throw new InvalidStudentIdentityApplicationStateTransitionException(status, "approve");
        }
        requireReviewer(reviewerId, reviewedAt);
        this.status = StudentIdentityApplicationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = null;
    }

    public void reject(UUID reviewerId, Instant reviewedAt, String reason) {
        if (status != StudentIdentityApplicationStatus.PENDING) {
            throw new InvalidStudentIdentityApplicationStateTransitionException(status, "reject");
        }
        requireReviewer(reviewerId, reviewedAt);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason required");
        }
        this.status = StudentIdentityApplicationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = reason;
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.userId == null) throw new IllegalArgumentException("userId required");
        if (b.schoolId == null) throw new IllegalArgumentException("schoolId required");
        requireText(b.realName, "realName");
        requireText(b.studentNumber, "studentNumber");
        requireText(b.grade, "grade");
        requireText(b.className, "className");
    }

    private static void requireReviewer(UUID reviewerId, Instant reviewedAt) {
        if (reviewerId == null) throw new IllegalArgumentException("reviewerId required");
        if (reviewedAt == null) throw new IllegalArgumentException("reviewedAt required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
    }

    public StudentIdentityApplicationId id() { return id; }
    public UUID userId() { return userId; }
    public UUID schoolId() { return schoolId; }
    public String realName() { return realName; }
    public String studentNumber() { return studentNumber; }
    public String grade() { return grade; }
    public String className() { return className; }
    public String evidenceFileKey() { return evidenceFileKey; }
    public StudentIdentityApplicationStatus status() { return status; }
    public UUID reviewedBy() { return reviewedBy; }
    public Instant reviewedAt() { return reviewedAt; }
    public String rejectionReason() { return rejectionReason; }

    public static class Builder {
        private StudentIdentityApplicationId id;
        private UUID userId;
        private UUID schoolId;
        private String realName;
        private String studentNumber;
        private String grade;
        private String className;
        private String evidenceFileKey;
        private StudentIdentityApplicationStatus status;
        private UUID reviewedBy;
        private Instant reviewedAt;
        private String rejectionReason;

        public Builder id(StudentIdentityApplicationId v) { this.id = v; return this; }
        public Builder userId(UUID v) { this.userId = v; return this; }
        public Builder schoolId(UUID v) { this.schoolId = v; return this; }
        public Builder realName(String v) { this.realName = v; return this; }
        public Builder studentNumber(String v) { this.studentNumber = v; return this; }
        public Builder grade(String v) { this.grade = v; return this; }
        public Builder className(String v) { this.className = v; return this; }
        public Builder evidenceFileKey(String v) { this.evidenceFileKey = v; return this; }
        public Builder status(StudentIdentityApplicationStatus v) { this.status = v; return this; }
        public Builder reviewedBy(UUID v) { this.reviewedBy = v; return this; }
        public Builder reviewedAt(Instant v) { this.reviewedAt = v; return this; }
        public Builder rejectionReason(String v) { this.rejectionReason = v; return this; }
    }
}
