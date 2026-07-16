package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.port.L3AuthorizationRepository;
import com.campusguinness.ranking.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class L3AuthorizationApplicationServiceTest {
    @Mock L3AuthorizationRepository repo;
    L3AuthorizationApplicationService svc;
    @BeforeEach void setUp() { svc = new L3AuthorizationApplicationService(repo); }
    @Test void submit() { var r=svc.submit(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()); assertThat(r.status()).isEqualTo("PENDING_REVIEW"); verify(repo).save(any()); }
    @Test void approve() { var a=submitted(); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.approve(a.id().value(),UUID.randomUUID(),"ok").status()).isEqualTo("APPROVED"); }
    @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.approve(UUID.randomUUID(),UUID.randomUUID(),"ok")).isInstanceOf(IllegalArgumentException.class); }
    private L3Authorization submitted() { var a=L3Authorization.create(new L3Authorization.Builder().id(new L3AuthorizationId(UUID.randomUUID())).schoolId(UUID.randomUUID()).projectId(UUID.randomUUID()).ruleVersionId(UUID.randomUUID())); a.submit(); return a; }
}
