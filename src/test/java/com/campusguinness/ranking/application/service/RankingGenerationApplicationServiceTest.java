package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.port.RankingGenerationRepository;
import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import com.campusguinness.ranking.application.query.port.RankingGenerationQueryPort;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingGenerationApplicationServiceTest {
    @Mock RankingDefinitionRepository definitions;
    @Mock RankingGenerationQueryPort sourceQuery;
    @Mock RankingGenerationRepository generationRepository;
    @Mock SchoolResourceAuthorization authorization;
    RankingGenerationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RankingGenerationApplicationService(definitions, sourceQuery, generationRepository, authorization);
    }

    @Test
    void generatePersistsSnapshotForAuthorizedSameSchoolDefinition() {
        UUID schoolId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID activityProjectId = UUID.randomUUID();
        RankingDefinition definition = definition(schoolId, projectId, activityProjectId);
        RankingGenerationContext context = context(schoolId, projectId, activityProjectId);
        when(definitions.findByIdForUpdate(any())).thenReturn(Optional.of(definition));
        when(sourceQuery.findContext(activityProjectId)).thenReturn(Optional.of(context));
        when(sourceQuery.findAuthoritativeEffectiveScores(activityProjectId, schoolId)).thenReturn(List.of(
                new RankingGenerationSourceRow(UUID.randomUUID(), UUID.randomUUID(), "Alice", new BigDecimal("10"), null, null)
        ));
        when(generationRepository.saveGeneratedSnapshot(any(), any(), any(), any()))
                .thenReturn(new RankingGenerationResult(definition.id().value(), UUID.randomUUID(), 1, 1, "GENERATED", java.time.Instant.now()));

        var result = service.generate(definition.id().value());

        assertThat(result.status()).isEqualTo("GENERATED");
        verify(authorization).requireSchoolAdmin(schoolId);
        verify(sourceQuery).findAuthoritativeEffectiveScores(activityProjectId, schoolId);
        ArgumentCaptor<GeneratedRankingSnapshot> snapshotCaptor = ArgumentCaptor.forClass(GeneratedRankingSnapshot.class);
        verify(generationRepository).saveGeneratedSnapshot(eq(definition), any(), eq(context), snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().entries()).hasSize(1);
    }

    @Test
    void generateRejectsMismatchedScope() {
        UUID schoolId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID activityProjectId = UUID.randomUUID();
        RankingDefinition definition = definition(schoolId, projectId, activityProjectId);
        when(definitions.findByIdForUpdate(any())).thenReturn(Optional.of(definition));
        when(sourceQuery.findContext(activityProjectId)).thenReturn(Optional.of(
                context(UUID.randomUUID(), projectId, activityProjectId)));
        when(authorization.requireSchoolAdmin(schoolId)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.generate(definition.id().value()))
                .isInstanceOf(IllegalStateException.class);
        verify(generationRepository, never()).saveGeneratedSnapshot(any(), any(), any(), any());
    }

    @Test
    void generateRejectsNonL1Definition() {
        RankingDefinition definition = RankingDefinition.create(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID()))
                .layer(RankingLayer.L2)
                .name("t")
                .schoolId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .dimensionFilters("{\"activityProjectId\":\"" + UUID.randomUUID() + "\"}")
                .createdBy(UUID.randomUUID()));
        when(definitions.findByIdForUpdate(any())).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.generate(definition.id().value()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(sourceQuery, generationRepository);
    }

    @Test
    void generateRejectsDisabledDefinition() {
        UUID schoolId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID activityProjectId = UUID.randomUUID();
        RankingDefinition definition = definition(schoolId, projectId, activityProjectId);
        definition.disable();
        when(definitions.findByIdForUpdate(any())).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.generate(definition.id().value()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definition is disabled");
        verifyNoInteractions(sourceQuery, generationRepository);
    }

    private RankingDefinition definition(UUID schoolId, UUID projectId, UUID activityProjectId) {
        return RankingDefinition.create(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID()))
                .layer(RankingLayer.L1)
                .name("t")
                .schoolId(schoolId)
                .projectId(projectId)
                .dimensionFilters("{\"activityProjectId\":\"" + activityProjectId + "\"}")
                .createdBy(UUID.randomUUID()));
    }

    private RankingGenerationContext context(UUID schoolId, UUID projectId, UUID activityProjectId) {
        return new RankingGenerationContext(activityProjectId, UUID.randomUUID(), "Activity", schoolId,
                "Campus School", projectId, "Project", UUID.randomUUID(), 1, "INTEGER", "HIGHER_BETTER", null, null);
    }
}
