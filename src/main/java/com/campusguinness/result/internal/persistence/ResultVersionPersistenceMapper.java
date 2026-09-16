package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;

final class ResultVersionPersistenceMapper {
    private ResultVersionPersistenceMapper() {}

    static ResultVersionEntity toEntity(ResultVersion domain) {
        var e = new ResultVersionEntity();
        e.setId(domain.id().value());
        e.setResultId(domain.resultId().value());
        e.setVersionNumber(domain.versionNumber());
        e.setTitle(domain.title());
        e.setSummaryText(domain.summaryText());
        e.setScoreHighlights(domain.scoreHighlights());
        e.setMediaRefs(domain.mediaRefs());
        e.setCoreContentModified(domain.coreContentModified());
        e.setFormatChangeLog(domain.formatChangeLog());
        e.setPublishedInternallyAt(domain.publishedInternallyAt());
        e.setPublishedPubliclyAt(domain.publishedPubliclyAt());
        e.setCreatedAt(domain.createdAt());
        return e;
    }

    static ResultVersion toDomain(ResultVersionEntity e) {
        return ResultVersion.reconstitute(new ResultVersion.Builder()
                .id(new ResultVersionId(e.getId()))
                .resultId(new ActivityResultId(e.getResultId()))
                .versionNumber(e.getVersionNumber())
                .title(e.getTitle())
                .summaryText(e.getSummaryText())
                .scoreHighlights(e.getScoreHighlights())
                .mediaRefs(e.getMediaRefs())
                .coreContentModified(e.isCoreContentModified())
                .formatChangeLog(e.getFormatChangeLog())
                .publishedInternallyAt(e.getPublishedInternallyAt())
                .publishedPubliclyAt(e.getPublishedPubliclyAt()),
                e.getCreatedAt());
    }
}
