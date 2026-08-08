package com.campusguinness.ranking.application.service;

import com.campusguinness.infrastructure.security.CurrentActor;
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
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class L3AuthorizationApplicationServiceTest {
    @Mock L3AuthorizationRepository repo;
    @Mock CurrentActor currentActor;
    L3AuthorizationApplicationService svc;
    UUID actorUserId;
    @BeforeEach void setUp() { actorUserId=UUID.randomUUID(); lenient().when(currentActor.requireUserId()).thenReturn(actorUserId); svc = new L3AuthorizationApplicationService(repo, currentActor); }
    @Test void submit() { var r=svc.submit(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()); assertThat(r.status()).isEqualTo("PENDING_REVIEW"); verify(repo).save(any()); }
    @Test void approve() { var a=submitted(); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.approve(a.id().value(),"ok").status()).isEqualTo("APPROVED"); var captor=forClass(L3Authorization.class); verify(repo).save(captor.capture()); assertThat(captor.getValue().reviewedBy()).isEqualTo(actorUserId); }
    @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.approve(UUID.randomUUID(),"ok")).isInstanceOf(IllegalArgumentException.class); }
    private L3Authorization submitted() { var a=L3Authorization.create(new L3Authorization.Builder().id(new L3AuthorizationId(UUID.randomUUID())).schoolId(UUID.randomUUID()).projectId(UUID.randomUUID()).ruleVersionId(UUID.randomUUID())); a.submit(); return a; }
}
