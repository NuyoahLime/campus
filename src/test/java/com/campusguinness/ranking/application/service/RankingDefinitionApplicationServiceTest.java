package com.campusguinness.ranking.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
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
    RankingDefinitionApplicationService svc;
    UUID actorUserId;
    @BeforeEach void setUp() { actorUserId = UUID.randomUUID(); lenient().when(currentActor.requireUserId()).thenReturn(actorUserId); svc = new RankingDefinitionApplicationService(repo, currentActor); }
    @Test void create() { var r=svc.create(RankingLayer.L1,"t",null,UUID.randomUUID()); assertThat(r.enabled()).isTrue(); var captor=forClass(RankingDefinition.class); verify(repo).save(captor.capture()); assertThat(captor.getValue().createdBy()).isEqualTo(actorUserId); }
    @Test void disable() { var d=def(); when(repo.findById(any())).thenReturn(Optional.of(d)); assertThat(svc.disable(d.id().value()).enabled()).isFalse(); }
    @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.disable(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); }
    private RankingDefinition def() { return RankingDefinition.create(new RankingDefinition.Builder().id(new RankingDefinitionId(UUID.randomUUID())).layer(RankingLayer.L1).name("t").projectId(UUID.randomUUID()).createdBy(UUID.randomUUID())); }
}
