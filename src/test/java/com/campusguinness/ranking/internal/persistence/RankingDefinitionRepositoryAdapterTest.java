package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.internal.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingDefinitionRepositoryAdapterTest {
    @Mock RankingDefinitionJpaRepository jpa;
    @InjectMocks RankingDefinitionRepositoryAdapter adapter;
    @Test void save() { adapter.save(def()); verify(jpa).save(any()); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new RankingDefinitionId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresNoEvents() { var e=ent(); when(jpa.findById(e.getId())).thenReturn(Optional.of(e)); assertThat(adapter.findById(new RankingDefinitionId(e.getId())).get().domainEvents()).isEmpty(); }
    private RankingDefinition def() { return RankingDefinition.create(new RankingDefinition.Builder().id(new RankingDefinitionId(UUID.randomUUID())).layer(RankingLayer.L1).name("t").projectId(UUID.randomUUID()).createdBy(UUID.randomUUID())); }
    private RankingDefinitionEntity ent() { var e=new RankingDefinitionEntity(); e.setId(UUID.randomUUID()); e.setLayer("L1"); e.setName("t"); e.setProjectId(UUID.randomUUID()); e.setCreatedBy(UUID.randomUUID()); e.setEnabled(true); return e; }
}
