package com.campusguinness.result.internal.domain;

import java.time.Instant;

/** Immutable ActivityResult content snapshot. */
public final class ResultVersion {
    private final ResultVersionId id;
    private final ActivityResultId resultId;
    private final int versionNumber;
    private final String title;
    private final String summaryText;
    private final String scoreHighlights;
    private final String mediaRefs;
    private final boolean coreContentModified;
    private final String formatChangeLog;
    private final Instant publishedInternallyAt;
    private final Instant publishedPubliclyAt;
    private final Instant createdAt;

    private ResultVersion(Builder b, Instant createdAt) {
        this.id = b.id;
        this.resultId = b.resultId;
        this.versionNumber = b.versionNumber;
        this.title = b.title;
        this.summaryText = b.summaryText;
        this.scoreHighlights = b.scoreHighlights;
        this.mediaRefs = b.mediaRefs;
        this.coreContentModified = b.coreContentModified;
        this.formatChangeLog = b.formatChangeLog;
        this.publishedInternallyAt = b.publishedInternallyAt;
        this.publishedPubliclyAt = b.publishedPubliclyAt;
        this.createdAt = createdAt;
    }

    public static ResultVersion create(Builder builder) {
        validate(builder);
        return new ResultVersion(builder, Instant.now());
    }

    public static ResultVersion reconstitute(Builder builder, Instant createdAt) {
        validate(builder);
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
        return new ResultVersion(builder, createdAt);
    }

    private static void validate(Builder b) {
        if (b.id == null) throw new IllegalArgumentException("id required");
        if (b.resultId == null) throw new IllegalArgumentException("resultId required");
        if (b.versionNumber < 1) throw new IllegalArgumentException("versionNumber must be positive");
        if (b.title == null || b.title.isBlank()) throw new IllegalArgumentException("title required");
        if (b.title.length() > 200) throw new IllegalArgumentException("title max 200 chars");
        if (b.summaryText == null) throw new IllegalArgumentException("summaryText required");
    }

    public ResultVersionId id() { return id; }
    public ActivityResultId resultId() { return resultId; }
    public int versionNumber() { return versionNumber; }
    public String title() { return title; }
    public String summaryText() { return summaryText; }
    public String scoreHighlights() { return scoreHighlights; }
    public String mediaRefs() { return mediaRefs; }
    public boolean coreContentModified() { return coreContentModified; }
    public String formatChangeLog() { return formatChangeLog; }
    public Instant publishedInternallyAt() { return publishedInternallyAt; }
    public Instant publishedPubliclyAt() { return publishedPubliclyAt; }
    public Instant createdAt() { return createdAt; }

    public static final class Builder {
        private ResultVersionId id;
        private ActivityResultId resultId;
        private int versionNumber;
        private String title;
        private String summaryText;
        private String scoreHighlights;
        private String mediaRefs;
        private boolean coreContentModified = true;
        private String formatChangeLog;
        private Instant publishedInternallyAt;
        private Instant publishedPubliclyAt;

        public Builder id(ResultVersionId v) { id = v; return this; }
        public Builder resultId(ActivityResultId v) { resultId = v; return this; }
        public Builder versionNumber(int v) { versionNumber = v; return this; }
        public Builder title(String v) { title = v; return this; }
        public Builder summaryText(String v) { summaryText = v; return this; }
        public Builder scoreHighlights(String v) { scoreHighlights = v; return this; }
        public Builder mediaRefs(String v) { mediaRefs = v; return this; }
        public Builder coreContentModified(boolean v) { coreContentModified = v; return this; }
        public Builder formatChangeLog(String v) { formatChangeLog = v; return this; }
        public Builder publishedInternallyAt(Instant v) { publishedInternallyAt = v; return this; }
        public Builder publishedPubliclyAt(Instant v) { publishedPubliclyAt = v; return this; }
    }
}
