package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.ranking.application.port.L3AuthorizationValidationPort;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class L3RankingDefinitionApplicationServiceTest {
    @Mock RankingDefinitionRepository repo;
    @Mock PlatformGovernanceAuthorization authorization;
    @Mock L3AuthorizationValidationPort validation;
    L3RankingDefinitionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new L3RankingDefinitionApplicationService(repo, authorization, validation);
    }

    @Test
    void createStoresL3DefinitionWithNullSchoolScope() {
        UUID actorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ruleVersionId = UUID.randomUUID();
        when(authorization.requireSuperAdmin()).thenReturn(actorId);

        var result = service.create("L3 Definition", projectId, ruleVersionId);

        ArgumentCaptor<RankingDefinition> captor = ArgumentCaptor.forClass(RankingDefinition.class);
        verify(validation).validateProjectRuleVersion(projectId, ruleVersionId);
        verify(repo).save(captor.capture());
        RankingDefinition saved = captor.getValue();
        assertThat(saved.layer()).isEqualTo(RankingLayer.L3);
        assertThat(saved.schoolId()).isNull();
        assertThat(saved.projectId()).isEqualTo(projectId);
        assertThat(saved.dimensionFilters()).isEqualTo("{\"ruleVersionId\":\"" + ruleVersionId + "\"}");
        assertThat(saved.createdBy()).isEqualTo(actorId);
        assertThat(result.enabled()).isTrue();
    }
}
