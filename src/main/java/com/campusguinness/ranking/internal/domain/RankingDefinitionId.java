package com.campusguinness.ranking.internal.domain;
import java.util.UUID;
public record RankingDefinitionId(UUID value) {
    public RankingDefinitionId { if (value == null) throw new IllegalArgumentException("id must not be null"); }
}
