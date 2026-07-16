package com.campusguinness.appeal.application.service;

import com.campusguinness.appeal.application.port.ScoreAppealRepository;
import com.campusguinness.appeal.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAppealApplicationServiceTest {
    @Mock ScoreAppealRepository repo;
    ScoreAppealApplicationService svc;

    @BeforeEach void setUp() { svc = new ScoreAppealApplicationService(repo); }

    private ScoreAppeal appeal() { return ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID())).schoolId(UUID.randomUUID()).scoreAttemptId(UUID.randomUUID()).studentId(UUID.randomUUID()).appealType("SCORE").appealReason("r")); }

    @Nested class Submit {
        @Test void success() { assertThat(svc.submit(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"SCORE","r").status()).isEqualTo("SUBMITTED"); verify(repo).save(any()); }
    }
    @Nested class BeginProcessing {
        @Test void success() { var a=appeal(); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.beginProcessing(a.id().value(),UUID.randomUUID()).status()).isEqualTo("PROCESSING"); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.beginProcessing(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
    @Nested class Reject {
        @Test void success() { var a=appeal(); a.beginProcessing(UUID.randomUUID()); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.reject(a.id().value(),"no").status()).isEqualTo("REJECTED"); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.reject(UUID.randomUUID(),"no")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
    @Nested class Withdraw {
        @Test void success() { var a=appeal(); a.beginProcessing(UUID.randomUUID()); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.withdraw(a.id().value()).status()).isEqualTo("WITHDRAWN"); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.withdraw(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
    @Nested class Resolve {
        @Test void success() { var a=appeal(); a.beginProcessing(UUID.randomUUID()); a.acceptPendingCorrection(); a.beginScoreCorrecting(); when(repo.findById(any())).thenReturn(Optional.of(a)); assertThat(svc.resolve(a.id().value(),"done").status()).isEqualTo("RESOLVED"); verify(repo).save(any()); }
        @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.resolve(UUID.randomUUID(),"done")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); }
    }
}
