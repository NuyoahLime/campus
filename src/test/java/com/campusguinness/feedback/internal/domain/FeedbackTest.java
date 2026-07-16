package com.campusguinness.feedback.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Feedback aggregate")
class FeedbackTest {

    private Feedback.Builder validBuilder() {
        return new Feedback.Builder()
                .id(new FeedbackId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID()).submitterId(UUID.randomUUID())
                .feedbackType("GENERAL").content("平台使用建议");
    }

    private Feedback createSubmitted() { return Feedback.create(validBuilder()); }

    @Nested @DisplayName("Creation")
    class Creation {
        @Test @DisplayName("creates in SUBMITTED status")
        void shouldCreateInSubmitted() {
            assertThat(createSubmitted().status()).isEqualTo(FeedbackStatus.SUBMITTED);
        }
        @Test @DisplayName("null content rejected")
        void shouldRejectNullContent() {
            assertThatThrownBy(() -> Feedback.create(validBuilder().content(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested @DisplayName("State transitions")
    class StateTransitions {
        @Test @DisplayName("SUBMITTED → PROCESSING")
        void shouldBeginProcessing() {
            var f = createSubmitted(); f.beginProcessing(UUID.randomUUID());
            assertThat(f.status()).isEqualTo(FeedbackStatus.PROCESSING);
        }
        @Test @DisplayName("PROCESSING → RESOLVED")
        void shouldResolve() {
            var f = createSubmitted(); f.beginProcessing(UUID.randomUUID());
            f.resolve("已处理完成");
            assertThat(f.status()).isEqualTo(FeedbackStatus.RESOLVED);
        }
        @Test @DisplayName("PROCESSING → ESCALATED")
        void shouldEscalate() {
            var f = createSubmitted(); f.beginProcessing(UUID.randomUUID());
            f.escalate();
            assertThat(f.status()).isEqualTo(FeedbackStatus.ESCALATED);
        }
        @Test @DisplayName("ESCALATED → PROCESSING (cycle back)")
        void shouldReturnFromEscalated() {
            var f = createSubmitted(); f.beginProcessing(UUID.randomUUID());
            f.escalate(); f.beginProcessing(UUID.randomUUID());
            assertThat(f.status()).isEqualTo(FeedbackStatus.PROCESSING);
        }
        @Test @DisplayName("SUBMITTED → CLOSED")
        void shouldCloseFromSubmitted() {
            var f = createSubmitted(); f.close("不再需要");
            assertThat(f.status()).isEqualTo(FeedbackStatus.CLOSED);
        }
        @Test @DisplayName("PROCESSING → CLOSED")
        void shouldCloseFromProcessing() {
            var f = createSubmitted(); f.beginProcessing(UUID.randomUUID());
            f.close("已处理完毕");
            assertThat(f.status()).isEqualTo(FeedbackStatus.CLOSED);
        }
        @Test @DisplayName("RESOLVED → CLOSED")
        void shouldCloseFromResolved() {
            var f = createSubmitted(); f.beginProcessing(UUID.randomUUID());
            f.resolve("done"); f.close("用户确认");
            assertThat(f.status()).isEqualTo(FeedbackStatus.CLOSED);
        }
    }

    @Nested @DisplayName("Terminal states")
    class TerminalStates {
        @Test @DisplayName("CLOSED → any rejected")
        void closedIsTerminal() {
            var f = createSubmitted(); f.close("done");
            assertThatThrownBy(() -> f.beginProcessing(UUID.randomUUID()))
                    .isInstanceOf(InvalidFeedbackStateTransitionException.class);
            assertThatThrownBy(() -> f.resolve("x"))
                    .isInstanceOf(InvalidFeedbackStateTransitionException.class);
        }
    }

    @Nested @DisplayName("Illegal transitions")
    class IllegalTransitions {
        @Test @DisplayName("Cannot resolve from SUBMITTED")
        void shouldRejectResolveFromSubmitted() {
            assertThatThrownBy(() -> createSubmitted().resolve("x"))
                    .isInstanceOf(InvalidFeedbackStateTransitionException.class);
        }
        @Test @DisplayName("Cannot escalate from SUBMITTED")
        void shouldRejectEscalateFromSubmitted() {
            assertThatThrownBy(() -> createSubmitted().escalate())
                    .isInstanceOf(InvalidFeedbackStateTransitionException.class);
        }
    }

    @Nested @DisplayName("Collection protection")
    class CollectionProtection {
        @Test @DisplayName("domain events unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            assertThatThrownBy(() -> createSubmitted().domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
