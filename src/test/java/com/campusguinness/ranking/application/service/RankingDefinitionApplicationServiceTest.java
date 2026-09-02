package com.campusguinness.ranking.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.ranking.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingDefinitionApplicationServiceTest {
    @Mock RankingDefinitionRepository repo;
    @Mock CurrentActor currentActor;
    @Mock SchoolResourceAuthorization authorization;
    RankingDefinitionApplicationService svc;
    UUID actorUserId;
    @BeforeEach void setUp() { actorUserId = UUID.randomUUID(); lenient().when(currentActor.requireUserId()).thenReturn(actorUserId); lenient().when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(UUID.randomUUID()); lenient().when(authorization.requireSchoolAdmin(any())).thenReturn(actorUserId); svc = new RankingDefinitionApplicationService(repo, authorization); }
    @Test void createL1DefinitionUsesDerivedSchoolScope() {
        UUID schoolId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID activityProjectId = UUID.randomUUID();
        when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        var r = svc.create(RankingLayer.L1,"t",schoolId,projectId,activityProjectId);
        assertThat(r.enabled()).isTrue();
        var captor = forClass(RankingDefinition.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().createdBy()).isEqualTo(actorUserId);
        assertThat(captor.getValue().schoolId()).isEqualTo(schoolId);
        assertThat(captor.getValue().dimensionFilters()).contains(activityProjectId.toString());
        verify(authorization).requireUniqueSchoolAdminSchool();
        verify(authorization).requireSchoolAdmin(schoolId);
    }
    @Test void createL1DefinitionRejectsForgedSchoolScope() {
        UUID actualSchoolId = UUID.randomUUID();
        UUID forgedSchoolId = UUID.randomUUID();
        when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(actualSchoolId);
        assertThatThrownBy(() -> svc.create(RankingLayer.L1, "t", forgedSchoolId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IdentityApplicationException.class)
                .extracting("code").isEqualTo("SCHOOL_ADMIN_SCOPE_DENIED");
        verify(repo, never()).save(any());
    }
    @Test void createRejectsNonL1Definition() {
        UUID schoolId = UUID.randomUUID();
        when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        assertThatThrownBy(() -> svc.create(RankingLayer.L3, "t", schoolId, UUID.randomUUID(), null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repo);
    }
    @Test void createL2DefinitionUsesChallengeProjectAndBestScorePolicy() {
        UUID schoolId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        var r = svc.create(RankingLayer.L2, "t", schoolId, projectId, null,
                "{\"grade\":\"G5\",\"className\":\"C1\"}");
        assertThat(r.enabled()).isTrue();
        var captor = forClass(RankingDefinition.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().layer()).isEqualTo(RankingLayer.L2);
        assertThat(captor.getValue().schoolId()).isEqualTo(schoolId);
        assertThat(captor.getValue().projectId()).isEqualTo(projectId);
        assertThat(captor.getValue().dimensionFilters()).contains("\"selectionPolicy\":\"BEST_SCORE\"");
        assertThat(captor.getValue().dimensionFilters()).contains("\"grade\":\"G5\"");
        assertThat(captor.getValue().dimensionFilters()).doesNotContain("activityProjectId");
    }
    @Test void createL2DefinitionRejectsActivityProjectId() {
        UUID schoolId = UUID.randomUUID();
        when(authorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        assertThatThrownBy(() -> svc.create(RankingLayer.L2, "t", schoolId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("L2 definitions must not use activityProjectId");
        verify(repo, never()).save(any());
    }
    @Test void disable() { var d=def(UUID.randomUUID()); when(repo.findById(any())).thenReturn(Optional.of(d)); assertThat(svc.disable(d.id().value()).enabled()).isFalse(); verify(authorization).requireSchoolAdmin(d.schoolId()); }
    @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.disable(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); }
    private RankingDefinition def(UUID schoolId) { return RankingDefinition.create(new RankingDefinition.Builder().id(new RankingDefinitionId(UUID.randomUUID())).layer(RankingLayer.L1).name("t").schoolId(schoolId).projectId(UUID.randomUUID()).dimensionFilters("{\"activityProjectId\":\"" + UUID.randomUUID() + "\"}").createdBy(UUID.randomUUID())); }
}
