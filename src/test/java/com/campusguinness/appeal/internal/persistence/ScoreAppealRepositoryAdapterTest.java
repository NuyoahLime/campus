package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.appeal.internal.domain.*;
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
class ScoreAppealRepositoryAdapterTest {
    @Mock ScoreAppealJpaRepository jpa;
    @InjectMocks ScoreAppealRepositoryAdapter adapter;
    @Test void insertNewUsesSaveAndFlush() { adapter.save(appeal()); verify(jpa).saveAndFlush(any()); }
    @Test void updateExistingUsesSaveAndFlush() { var e=ent(); var a=appeal(); a.beginProcessing(UUID.randomUUID()); when(jpa.findById(a.id().value())).thenReturn(Optional.of(e)); adapter.save(a); verify(jpa).saveAndFlush(e); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new ScoreAppealId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresNoEvents() { var e=ent(); when(jpa.findById(e.getId())).thenReturn(Optional.of(e)); assertThat(adapter.findById(new ScoreAppealId(e.getId())).get().domainEvents()).isEmpty(); }
    private ScoreAppeal appeal() { return ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID())).schoolId(UUID.randomUUID()).scoreAttemptId(UUID.randomUUID()).studentId(UUID.randomUUID()).appealType("SCORE").appealReason("r")); }
    private ScoreAppealEntity ent() { var e=new ScoreAppealEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID()); e.setScoreAttemptId(UUID.randomUUID()); e.setStudentId(UUID.randomUUID()); e.setAppealType("SCORE"); e.setAppealReason("r"); e.setAppealStatus("SUBMITTED"); return e; }
}
