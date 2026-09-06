package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.ranking.application.exception.RankingGenerationException;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.port.RankingGenerationRepository;
import com.campusguinness.ranking.application.query.model.L3GenerationCandidateRow;
import com.campusguinness.ranking.application.query.model.L3UsableAuthorizationResult;
import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import com.campusguinness.ranking.application.query.port.L3UsableAuthorizationQueryPort;
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
    @Mock PlatformGovernanceAuthorization platformAuthorization;
    @Mock L3UsableAuthorizationQueryPort usableAuthorizationQuery;
    RankingGenerationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RankingGenerationApplicationService(
                definitions, sourceQuery, generationRepository, authorization, platformAuthorization, usableAuthorizationQuery);
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
    void generateL3UsesUsableAuthorizationsAndSnapshotsPrivacy() {
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        UUID authId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID scoreAttemptId = UUID.randomUUID();
        RankingDefinition definition = RankingDefinition.create(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID()))
                .layer(RankingLayer.L3)
                .name("t")
                .schoolId(null)
                .projectId(projectId)
                .dimensionFilters("{\"ruleVersionId\":\"" + ruleVersionId + "\"}")
                .createdBy(UUID.randomUUID()));
        when(definitions.findByIdForUpdate(any())).thenReturn(Optional.of(definition));
        when(platformAuthorization.requireSuperAdmin()).thenReturn(UUID.randomUUID());
        when(sourceQuery.findL3Context(projectId, ruleVersionId)).thenReturn(Optional.of(
                new RankingGenerationContext(null, null, null, null, null, projectId, "Project",
                        ruleVersionId, 1, "INTEGER", "HIGHER_BETTER", null, null)));
        when(usableAuthorizationQuery.findUsableAuthorizations(projectId, ruleVersionId)).thenReturn(List.of(
                new L3UsableAuthorizationResult(authId, schoolId, projectId, ruleVersionId,
                        "{\"grades\":[\"G5\"],\"classNames\":[\"C1\"]}", true, false)
        ));
        when(sourceQuery.findL3CandidateScores(projectId, ruleVersionId)).thenReturn(List.of(
                new L3GenerationCandidateRow(scoreAttemptId, studentId, schoolId, "Campus School",
                        UUID.randomUUID(), "Activity", java.time.Instant.parse("2026-01-01T00:00:00Z"),
                        java.time.Instant.parse("2026-01-01T01:00:00Z"), "G5", "C1",
                        new BigDecimal("10"), null, null, UUID.randomUUID(), ruleVersionId)
        ));
        when(generationRepository.saveGeneratedSnapshot(any(), any(), any(), any()))
                .thenReturn(new RankingGenerationResult(definition.id().value(), UUID.randomUUID(), 1, 1, "GENERATED", java.time.Instant.now()));

        var result = service.generate(definition.id().value());

        assertThat(result.status()).isEqualTo("GENERATED");
        verify(platformAuthorization).requireSuperAdmin();
        ArgumentCaptor<GeneratedRankingSnapshot> snapshotCaptor = ArgumentCaptor.forClass(GeneratedRankingSnapshot.class);
        verify(generationRepository).saveGeneratedSnapshot(eq(definition), any(), any(), snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().authorizationIdsSnapshot()).containsExactly(authId);
        assertThat(snapshotCaptor.getValue().entries()).hasSize(1);
        assertThat(snapshotCaptor.getValue().entries().get(0).schoolName()).isEqualTo("Campus School");
        assertThat(snapshotCaptor.getValue().entries().get(0).studentDisplayName()).isEqualTo("匿名选手");
    }

    @Test
    void generateL3RejectsWhenNoUsableAuthorizationExists() {
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        RankingDefinition definition = RankingDefinition.create(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID()))
                .layer(RankingLayer.L3)
                .name("t")
                .schoolId(null)
                .projectId(projectId)
                .dimensionFilters("{\"ruleVersionId\":\"" + ruleVersionId + "\"}")
                .createdBy(UUID.randomUUID()));
        when(definitions.findByIdForUpdate(any())).thenReturn(Optional.of(definition));
        when(platformAuthorization.requireSuperAdmin()).thenReturn(UUID.randomUUID());
        when(sourceQuery.findL3Context(projectId, ruleVersionId)).thenReturn(Optional.of(
                new RankingGenerationContext(null, null, null, null, null, projectId, "Project",
                        ruleVersionId, 1, "INTEGER", "HIGHER_BETTER", null, null)));
        when(usableAuthorizationQuery.findUsableAuthorizations(projectId, ruleVersionId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(definition.id().value()))
                .isInstanceOf(RankingGenerationException.class)
                .hasMessageContaining("no usable L3 authorization");
        verify(generationRepository, never()).saveGeneratedSnapshot(any(), any(), any(), any());
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
