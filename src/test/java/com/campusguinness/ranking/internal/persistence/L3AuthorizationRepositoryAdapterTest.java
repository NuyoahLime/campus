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
class L3AuthorizationRepositoryAdapterTest {
    @Mock L3AuthorizationJpaRepository jpa;
    @InjectMocks L3AuthorizationRepositoryAdapter adapter;
    @Test void save() { adapter.save(auth()); verify(jpa).save(any()); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new L3AuthorizationId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresNoEvents() { var e=ent(); when(jpa.findById(e.getId())).thenReturn(Optional.of(e)); assertThat(adapter.findById(new L3AuthorizationId(e.getId())).get().domainEvents()).isEmpty(); }
    private L3Authorization auth() { return L3Authorization.create(new L3Authorization.Builder().id(new L3AuthorizationId(UUID.randomUUID())).schoolId(UUID.randomUUID()).projectId(UUID.randomUUID()).ruleVersionId(UUID.randomUUID())); }
    private L3AuthorizationEntity ent() { var e=new L3AuthorizationEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID()); e.setProjectId(UUID.randomUUID()); e.setRuleVersionId(UUID.randomUUID()); e.setAuthorizationStatus("DRAFT"); return e; }
}
