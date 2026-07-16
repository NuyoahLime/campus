package com.campusguinness.activity.application.query;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
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
class ActivityQueryServiceTest {
    @Mock ActivityQueryPort port;
    ActivityQueryService svc;
    @BeforeEach void setUp() { svc = new ActivityQueryService(port); }

    @Test void delegatesWithPublicStatuses() {
        when(port.findPublic(eq(1), eq(10), any())).thenReturn(new QueryPage<>(Collections.emptyList(), 1, 10, 0));
        svc.listPublic(1, 10);
        verify(port).findPublic(1, 10, List.of("PUBLISHED", "IN_PROGRESS", "ENDED"));
    }
    @Test void rejectsNegativePage() { assertThatThrownBy(()->svc.listPublic(-1,20)).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsZeroSize() { assertThatThrownBy(()->svc.listPublic(0,0)).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsExcessiveSize() { assertThatThrownBy(()->svc.listPublic(0,101)).isInstanceOf(IllegalArgumentException.class); }
    @Test void returnsEmptyPage() {
        when(port.findPublic(0, 20, List.of("PUBLISHED","IN_PROGRESS","ENDED"))).thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
        assertThat(svc.listPublic(0, 20).items()).isEmpty();
    }
}
