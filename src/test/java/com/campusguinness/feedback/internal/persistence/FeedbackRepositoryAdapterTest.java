package com.campusguinness.feedback.internal.persistence;

import com.campusguinness.feedback.internal.domain.*;
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
class FeedbackRepositoryAdapterTest {
    @Mock FeedbackJpaRepository jpa;
    @InjectMocks FeedbackRepositoryAdapter adapter;
    @Test void save() { adapter.save(fb()); verify(jpa).saveAndFlush(any()); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new FeedbackId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresNoEvents() { var e=ent(); when(jpa.findById(e.getId())).thenReturn(Optional.of(e)); assertThat(adapter.findById(new FeedbackId(e.getId())).get().domainEvents()).isEmpty(); }
    private Feedback fb() { return Feedback.create(new Feedback.Builder().id(new FeedbackId(UUID.randomUUID())).feedbackType("GENERAL").content("t")); }
    private FeedbackEntity ent() { var e=new FeedbackEntity(); e.setId(UUID.randomUUID()); e.setFeedbackType("GENERAL"); e.setContent("t"); e.setFeedbackStatus("SUBMITTED"); return e; }
}
