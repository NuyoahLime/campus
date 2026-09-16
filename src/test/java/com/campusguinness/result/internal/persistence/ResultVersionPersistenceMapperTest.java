package com.campusguinness.result.internal.persistence;

import com.campusguinness.result.internal.domain.ActivityResultId;
import com.campusguinness.result.internal.domain.ResultVersion;
import com.campusguinness.result.internal.domain.ResultVersionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResultVersionPersistenceMapperTest {

    @Test
    void entityDomainEntityRoundTripPreservesImmutableSnapshot() {
        var entity = new ResultVersionEntity();
        entity.setId(UUID.randomUUID());
        entity.setResultId(UUID.randomUUID());
        entity.setVersionNumber(3);
        entity.setTitle("Campus record");
        entity.setSummaryText("Summary");
        entity.setScoreHighlights("{\"score\":42}");
        entity.setMediaRefs("[{\"id\":\"media-1\"}]");
        entity.setCoreContentModified(true);
        entity.setFormatChangeLog("format note");
        entity.setPublishedInternallyAt(Instant.parse("2026-01-02T00:00:00Z"));
        entity.setPublishedPubliclyAt(Instant.parse("2026-01-03T00:00:00Z"));
        entity.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        ResultVersion domain = ResultVersionPersistenceMapper.toDomain(entity);
        ResultVersionEntity roundTripped = ResultVersionPersistenceMapper.toEntity(domain);

        assertThat(roundTripped.getId()).isEqualTo(entity.getId());
        assertThat(roundTripped.getResultId()).isEqualTo(entity.getResultId());
        assertThat(roundTripped.getVersionNumber()).isEqualTo(3);
        assertThat(roundTripped.getTitle()).isEqualTo("Campus record");
        assertThat(roundTripped.getSummaryText()).isEqualTo("Summary");
        assertThat(roundTripped.getScoreHighlights()).isEqualTo("{\"score\":42}");
        assertThat(roundTripped.getMediaRefs()).isEqualTo("[{\"id\":\"media-1\"}]");
        assertThat(roundTripped.isCoreContentModified()).isTrue();
        assertThat(roundTripped.getFormatChangeLog()).isEqualTo("format note");
        assertThat(roundTripped.getPublishedInternallyAt()).isEqualTo(entity.getPublishedInternallyAt());
        assertThat(roundTripped.getPublishedPubliclyAt()).isEqualTo(entity.getPublishedPubliclyAt());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void createBuildsImmutableDomainSnapshot() {
        ResultVersion version = ResultVersion.create(new ResultVersion.Builder()
                .id(new ResultVersionId(UUID.randomUUID()))
                .resultId(new ActivityResultId(UUID.randomUUID()))
                .versionNumber(1)
                .title("First")
                .summaryText("First snapshot"));

        assertThat(version.versionNumber()).isOne();
        assertThat(version.createdAt()).isNotNull();
        assertThat(version.coreContentModified()).isTrue();
    }
}
