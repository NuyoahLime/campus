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
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAttemptApplicationServiceTest {
    @Mock ScoreAttemptRepository repo;
    ScoreAttemptApplicationService svc;
    @BeforeEach void setUp() { svc = new ScoreAttemptApplicationService(repo); }

    @Test void shouldSubmit() {
        var r = svc.submit(new SubmitScoreCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, ScoreStorageType.INTEGER, new ScoreValue.IntegerScore(100),
                Instant.now(), "teacher", UUID.randomUUID()));
        assertThat(r.status()).isEqualTo("PENDING_REVIEW");
        verify(repo).save(any());
    }
}
