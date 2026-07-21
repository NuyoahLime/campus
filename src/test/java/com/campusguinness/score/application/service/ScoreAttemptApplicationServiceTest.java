package com.campusguinness.score.application.service;

import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.result.ScoreAttemptResult;
import com.campusguinness.score.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAttemptApplicationServiceTest {
    @Mock ScoreAttemptRepository repo;
    ScoreAttemptApplicationService svc;
    private final UUID schoolId = UUID.randomUUID();

    @BeforeEach void setUp() { svc = new ScoreAttemptApplicationService(repo); }

    @Test void shouldSubmit() {
        var r = svc.submit(new SubmitScoreCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, ScoreStorageType.INTEGER, new ScoreValue.IntegerScore(100),
                Instant.now(), "teacher", UUID.randomUUID()));
        assertThat(r.status()).isEqualTo("PENDING_REVIEW");
        verify(repo).save(any());
    }

    @Test void shouldFindBySchool() {
        var s = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID())).schoolId(schoolId)
                .activityProjectId(UUID.randomUUID()).studentId(UUID.randomUUID())
                .attemptNumber(1).scoreStorageType(ScoreStorageType.INTEGER)
                .scoreValue(new ScoreValue.IntegerScore(100))
                .scoreBusinessTime(Instant.now()).timeSource("teacher")
                .enteredBy(UUID.randomUUID()));
        s.submit();
        when(repo.findBySchoolId(schoolId)).thenReturn(List.of(s));
        List<ScoreAttemptResult> results = svc.findBySchool(schoolId);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().status()).isEqualTo("PENDING_REVIEW");
    }

    @Test void shouldReturnEmptyForSchoolWithNoScores() {
        when(repo.findBySchoolId(schoolId)).thenReturn(List.of());
        assertThat(svc.findBySchool(schoolId)).isEmpty();
    }
}
