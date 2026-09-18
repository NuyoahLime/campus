package com.campusguinness.result.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface ResultVersionJpaRepository extends JpaRepository<ResultVersionEntity, UUID> {
    boolean existsByIdAndResultId(UUID id, UUID resultId);

    @Query("select coalesce(max(v.versionNumber), 0) from ResultVersionEntity v where v.resultId = :resultId")
    int maxVersionNumber(@Param("resultId") UUID resultId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE result_versions
            SET published_internally_at = :publishedAt
            WHERE id = :id
              AND published_internally_at IS NULL
            """, nativeQuery = true)
    int markPublishedInternallyIfUnpublished(
            @Param("id") UUID id,
            @Param("publishedAt") Instant publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE result_versions
            SET published_publicly_at = :publishedAt
            WHERE id = :id
              AND published_publicly_at IS NULL
            """, nativeQuery = true)
    int markPublishedPubliclyIfUnpublished(
            @Param("id") UUID id,
            @Param("publishedAt") Instant publishedAt);
}
