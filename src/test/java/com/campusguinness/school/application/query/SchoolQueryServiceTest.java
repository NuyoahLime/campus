package com.campusguinness.school.application.query;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.school.application.query.model.SchoolListResult;
import com.campusguinness.school.application.query.port.SchoolQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolQueryServiceTest {
    @Mock SchoolQueryPort port;
    SchoolQueryService svc;
    @BeforeEach void setUp() { svc = new SchoolQueryService(port); }

    @Test void delegatesPageAndSize() {
        when(port.findNormal(2, 30)).thenReturn(new QueryPage<>(Collections.emptyList(), 2, 30, 0));
        svc.listNormal(2, 30);
        verify(port).findNormal(2, 30);
    }
    @Test void rejectsNegativePage() { assertThatThrownBy(()->svc.listNormal(-1,20)).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsZeroSize() { assertThatThrownBy(()->svc.listNormal(0,0)).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsExcessiveSize() { assertThatThrownBy(()->svc.listNormal(0,101)).isInstanceOf(IllegalArgumentException.class); }
    @Test void returnsEmptyPage() {
        when(port.findNormal(0, 20)).thenReturn(new QueryPage<>(Collections.emptyList(), 0, 20, 0));
        assertThat(svc.listNormal(0, 20).items()).isEmpty();
    }
}
