package com.campusguinness.feedback.internal.persistence;

import com.campusguinness.feedback.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("FeedbackPersistenceMapper")
class FeedbackPersistenceMapperTest {
    @Nested class AllStates {
        @ParameterizedTest @EnumSource(FeedbackStatus.class)
        void restoresState(FeedbackStatus s) {
            var e = entity(s.name());
            var f = FeedbackPersistenceMapper.toDomain(e);
            assertThat(f.status()).isEqualTo(s);
            assertThat(f.domainEvents()).isEmpty();
        }
    }
    @Nested class Fields {
        @org.junit.jupiter.api.Test void resolvedKeepsReply() {
            var e = entity("RESOLVED"); e.setReply("已处理");
            assertThat(FeedbackPersistenceMapper.toDomain(e).reply()).isEqualTo("已处理");
        }
        @org.junit.jupiter.api.Test void closedKeepsReason() {
            var e = entity("CLOSED"); e.setCloseReason("spam");
            assertThat(FeedbackPersistenceMapper.toDomain(e).closeReason()).isEqualTo("spam");
        }
    }
    @Nested class ToEntity {
        @org.junit.jupiter.api.Test void mapsToEntity() {
            var f = Feedback.create(new Feedback.Builder().id(new FeedbackId(UUID.randomUUID())).feedbackType("GENERAL").content("test"));
            assertThat(FeedbackPersistenceMapper.toEntity(f).getFeedbackStatus()).isEqualTo("SUBMITTED");
        }
    }
    private FeedbackEntity entity(String s) { var e=new FeedbackEntity(); e.setId(UUID.randomUUID()); e.setFeedbackType("GENERAL"); e.setContent("t"); e.setFeedbackStatus(s); return e; }
}
