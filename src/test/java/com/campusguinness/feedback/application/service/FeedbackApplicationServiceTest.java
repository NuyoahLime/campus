package com.campusguinness.feedback.application.service;

import com.campusguinness.feedback.application.port.FeedbackRepository;
import com.campusguinness.feedback.internal.domain.*;
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
class FeedbackApplicationServiceTest {
    @Mock FeedbackRepository repo;
    FeedbackApplicationService svc;
    @BeforeEach void setUp() { svc = new FeedbackApplicationService(repo); }
    private Feedback fb() { return Feedback.create(new Feedback.Builder().id(new FeedbackId(UUID.randomUUID())).feedbackType("GENERAL").content("t")); }

    @Nested class Submit { @Test void success() { assertThat(svc.submit(UUID.randomUUID(),UUID.randomUUID(),"GENERAL","t").status()).isEqualTo("SUBMITTED"); verify(repo).save(any()); } }
    @Nested class BeginProcessing { @Test void success() { var f=fb(); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.beginProcessing(f.id().value(),UUID.randomUUID()).status()).isEqualTo("PROCESSING"); verify(repo).save(any()); } @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.beginProcessing(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); } }
    @Nested class Resolve { @Test void success() { var f=fb(); f.beginProcessing(UUID.randomUUID()); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.resolve(f.id().value(),"done").status()).isEqualTo("RESOLVED"); verify(repo).save(any()); } @Test void notFound() { when(repo.findById(any())).thenReturn(Optional.empty()); assertThatThrownBy(()->svc.resolve(UUID.randomUUID(),"done")).isInstanceOf(IllegalArgumentException.class); verify(repo,never()).save(any()); } }
    @Nested class Close { @Test void success() { var f=fb(); when(repo.findById(any())).thenReturn(Optional.of(f)); assertThat(svc.close(f.id().value(),"done").status()).isEqualTo("CLOSED"); verify(repo).save(any()); } }
}
