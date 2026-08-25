package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAttemptRepositoryAdapterTest {
    @Mock ScoreAttemptJpaRepository jpa;
    @InjectMocks ScoreAttemptRepositoryAdapter adapter;

    @Test void saveCallsJpa() { when(jpa.findById(any())).thenReturn(Optional.empty()); adapter.save(draft()); verify(jpa).saveAndFlush(any()); }
    @Test void findByIdEmpty() { when(jpa.findById(any())).thenReturn(Optional.empty()); assertThat(adapter.findById(new ScoreAttemptId(UUID.randomUUID()))).isEmpty(); }
    @Test void restoresApprovedNoEvents() {
        var e = entity("APPROVED"); when(jpa.findById(e.getId())).thenReturn(Optional.of(e));
        var s = adapter.findById(new ScoreAttemptId(e.getId()));
        assertThat(s).isPresent(); assertThat(s.get().status()).isEqualTo(AttemptStatus.APPROVED);
        assertThat(s.get().domainEvents()).isEmpty();
    }
    @Test void corruptedEntityPropagatesException() {
        var e = entity("APPROVED"); e.setScoreValue(null); // corrupt INTEGER
        when(jpa.findById(e.getId())).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> adapter.findById(new ScoreAttemptId(e.getId())))
                .isInstanceOf(ScoreValuePersistenceException.class);
    }
    @Test void concurrentAttemptNumberConflictIsMappedToScoreConflict() {
        when(jpa.findById(any())).thenReturn(Optional.empty());
        var sql = new SQLException("duplicate key violates constraint uq_score_attempt_ap_student_num", "23505");
        when(jpa.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate", sql));

        assertThatThrownBy(() -> adapter.save(draft()))
                .isInstanceOf(com.campusguinness.score.application.exception.ScoreWriteException.class)
                .satisfies(error -> assertThat(((com.campusguinness.score.application.exception.ScoreWriteException) error).code())
                        .isEqualTo("SCORE_ATTEMPT_CONFLICT"));
    }
    private ScoreAttempt draft() { return ScoreAttempt.create(new ScoreAttempt.Builder().id(new ScoreAttemptId(UUID.randomUUID())).schoolId(UUID.randomUUID()).activityProjectId(UUID.randomUUID()).studentId(UUID.randomUUID()).attemptNumber(1).scoreStorageType(ScoreStorageType.INTEGER).scoreValue(new ScoreValue.IntegerScore(1)).enteredBy(UUID.randomUUID())); }
    private ScoreAttemptEntity entity(String s) { var e = new ScoreAttemptEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID()); e.setActivityProjectId(UUID.randomUUID()); e.setStudentId(UUID.randomUUID()); e.setAttemptNumber(1); e.setScoreStorageType("INTEGER"); e.setScoreValue(java.math.BigDecimal.valueOf(1)); e.setScoreStatus(s); e.setEnteredBy(UUID.randomUUID()); e.setCurrentEffective(false); e.setManualMakeup(false); return e; }
}
