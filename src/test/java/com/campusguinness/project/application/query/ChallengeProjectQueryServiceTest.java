package com.campusguinness.project.application.query;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.project.application.query.port.ChallengeProjectQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeProjectQueryServiceTest {
    @Mock ChallengeProjectQueryPort port;
    ChallengeProjectQueryService svc;
    @BeforeEach void setUp() { svc = new ChallengeProjectQueryService(port); }

    @Test void delegatesPageAndSize() {
        List<ChallengeProjectListResult> items = Collections.emptyList();
        when(port.findPublished(2, 30)).thenReturn(new QueryPage<>(items, 2, 30, 0));
        svc.listPublic(2, 30);
        verify(port).findPublished(2, 30);
    }
    @Test void rejectsNegativePage() { assertThatThrownBy(()->svc.listPublic(-1,20)).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsZeroSize() { assertThatThrownBy(()->svc.listPublic(0,0)).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsExcessiveSize() { assertThatThrownBy(()->svc.listPublic(0,101)).isInstanceOf(IllegalArgumentException.class); }
    @Test void returnsEmptyPage() {
        when(port.findPublished(0, 20)).thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
        var r = svc.listPublic(0, 20);
        assertThat(r.items()).isEmpty();
        assertThat(r.totalElements()).isEqualTo(0);
    }
}
