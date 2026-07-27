package com.campusguinness.activity.application.query;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.activity.application.query.port.ActivityQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityQueryServiceTest {

    @Mock ActivityQueryPort port;
    ActivityQueryService svc;

    @BeforeEach void setUp() { svc = new ActivityQueryService(port); }

    // ── listPublic ──

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

    // ── listBySchool ──

    @Nested @DisplayName("listBySchool")
    class ListBySchool {
        final UUID schoolId = UUID.randomUUID();

        @Test @DisplayName("valid executionStatus accepted")
        void validExecutionStatus() {
            var page = new QueryPage<>(Collections.<ActivityListResult>emptyList(), 0, 20, 0);
            when(port.findBySchool(eq(schoolId), eq("DRAFT"), isNull(), isNull(), eq(0), eq(20))).thenReturn(page);
            assertThat(svc.listBySchool(schoolId, "DRAFT", null, null, 0, 20)).isNotNull();
        }

        @Test @DisplayName("invalid executionStatus rejected")
        void invalidExecutionStatus() {
            assertThatThrownBy(() -> svc.listBySchool(schoolId, "NONEXISTENT", null, null, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid executionStatus");
        }

        @Test @DisplayName("valid publicStatus accepted")
        void validPublicStatus() {
            var page = new QueryPage<>(Collections.<ActivityListResult>emptyList(), 0, 20, 0);
            when(port.findBySchool(eq(schoolId), isNull(), eq("PUBLIC"), isNull(), eq(0), eq(20))).thenReturn(page);
            assertThat(svc.listBySchool(schoolId, null, "PUBLIC", null, 0, 20)).isNotNull();
        }

        @Test @DisplayName("invalid publicStatus rejected")
        void invalidPublicStatus() {
            assertThatThrownBy(() -> svc.listBySchool(schoolId, null, "BOGUS", null, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid publicStatus");
        }

        @Test @DisplayName("keyword is trimmed")
        void keywordTrimmed() {
            var page = new QueryPage<>(Collections.<ActivityListResult>emptyList(), 0, 20, 0);
            when(port.findBySchool(eq(schoolId), isNull(), isNull(), eq("hello"), eq(0), eq(20))).thenReturn(page);
            svc.listBySchool(schoolId, null, null, "  hello  ", 0, 20);
            verify(port).findBySchool(schoolId, null, null, "hello", 0, 20);
        }

        @Test @DisplayName("blank keyword normalized to null")
        void blankKeywordToNull() {
            var page = new QueryPage<>(Collections.<ActivityListResult>emptyList(), 0, 20, 0);
            when(port.findBySchool(eq(schoolId), isNull(), isNull(), isNull(), eq(0), eq(20))).thenReturn(page);
            svc.listBySchool(schoolId, null, null, "   ", 0, 20);
            verify(port).findBySchool(schoolId, null, null, null, 0, 20);
        }

        @Test @DisplayName("keyword at 100 chars accepted")
        void keywordAt100Chars() {
            String kw = "A".repeat(100);
            var page = new QueryPage<>(Collections.<ActivityListResult>emptyList(), 0, 20, 0);
            when(port.findBySchool(eq(schoolId), isNull(), isNull(), eq(kw), eq(0), eq(20))).thenReturn(page);
            assertThat(svc.listBySchool(schoolId, null, null, kw, 0, 20)).isNotNull();
        }

        @Test @DisplayName("keyword over 100 chars rejected")
        void keywordOver100Chars() {
            String kw = "A".repeat(101);
            assertThatThrownBy(() -> svc.listBySchool(schoolId, null, null, kw, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("keyword too long");
        }

        @Test @DisplayName("page negative rejected")
        void pageNegative() {
            assertThatThrownBy(() -> svc.listBySchool(schoolId, null, null, null, -1, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page");
        }

        @Test @DisplayName("size zero rejected")
        void sizeZero() {
            assertThatThrownBy(() -> svc.listBySchool(schoolId, null, null, null, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("size");
        }

        @Test @DisplayName("size over 100 rejected")
        void sizeOver100() {
            assertThatThrownBy(() -> svc.listBySchool(schoolId, null, null, null, 0, 101))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("size");
        }

        @Test @DisplayName("null schoolId rejected")
        void nullSchoolId() {
            assertThatThrownBy(() -> svc.listBySchool(null, null, null, null, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("schoolId");
        }
    }
}
