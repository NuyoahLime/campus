package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.port.RankingPublicationRepository;
import com.campusguinness.ranking.application.result.RankingPublicationResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingPublicationApplicationServiceTest {
    @Mock RankingDefinitionRepository definitions;
    @Mock RankingPublicationRepository publications;
    @Mock SchoolResourceAuthorization authorization;

    @Test
    void publishDelegatesExistingGeneratedSnapshotWithoutRecalculation() {
        UUID schoolId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        RankingDefinition definition = definition(definitionId, schoolId, RankingLayer.L1, true);
        when(definitions.findByIdForUpdate(new RankingDefinitionId(definitionId))).thenReturn(Optional.of(definition));
        when(publications.publishGeneratedVersion(definition, versionId))
                .thenReturn(new RankingPublicationResult(definitionId, versionId, null, versionId, "PUBLISHED", Instant.now()));

        new RankingPublicationApplicationService(definitions, publications, authorization).publish(definitionId, versionId);

        verify(authorization).requireSchoolAdmin(schoolId);
        verify(publications).publishGeneratedVersion(definition, versionId);
        verifyNoMoreInteractions(publications);
    }

    @Test
    void l2PublishDelegatesExistingGeneratedSnapshotWithoutRecalculation() {
        UUID schoolId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        RankingDefinition definition = definition(definitionId, schoolId, RankingLayer.L2, true);
        when(definitions.findByIdForUpdate(new RankingDefinitionId(definitionId))).thenReturn(Optional.of(definition));
        when(publications.publishGeneratedVersion(definition, versionId))
                .thenReturn(new RankingPublicationResult(definitionId, versionId, null, versionId, "PUBLISHED", Instant.now()));

        new RankingPublicationApplicationService(definitions, publications, authorization).publish(definitionId, versionId);

        verify(authorization).requireSchoolAdmin(schoolId);
        verify(publications).publishGeneratedVersion(definition, versionId);
        verifyNoMoreInteractions(publications);
    }

    @Test
    void l3DefinitionIsRejectedBeforePersistence() {
        UUID definitionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(definitions.findByIdForUpdate(new RankingDefinitionId(definitionId)))
                .thenReturn(Optional.of(definition(definitionId, UUID.randomUUID(), RankingLayer.L3, true)));

        assertThatThrownBy(() -> new RankingPublicationApplicationService(definitions, publications, authorization)
                .publish(definitionId, versionId))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(publications);
    }

    @Test
    void disabledDefinitionIsRejectedBeforePersistence() {
        UUID definitionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(definitions.findByIdForUpdate(new RankingDefinitionId(definitionId)))
                .thenReturn(Optional.of(definition(definitionId, UUID.randomUUID(), RankingLayer.L1, false)));

        assertThatThrownBy(() -> new RankingPublicationApplicationService(definitions, publications, authorization)
                .publish(definitionId, versionId))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(publications);
    }

    private RankingDefinition definition(UUID definitionId, UUID schoolId, RankingLayer layer, boolean enabled) {
        return RankingDefinition.reconstitute(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(definitionId))
                .layer(layer)
                .name("test")
                .schoolId(schoolId)
                .projectId(UUID.randomUUID())
                .dimensionFilters(layer == RankingLayer.L2
                        ? "{\"selectionPolicy\":\"BEST_SCORE\"}"
                        : "{\"activityProjectId\":\"" + UUID.randomUUID() + "\"}")
                .createdBy(UUID.randomUUID()), enabled, null);
    }
}
