package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.appeal.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ScoreAppealPersistenceMapper")
class ScoreAppealPersistenceMapperTest {

    @Nested @DisplayName("All 13 states round-trip")
    class AllStates {
        @ParameterizedTest @EnumSource(AppealStatus.class)
        @DisplayName("restores state without events")
        void restoresState(AppealStatus status) {
            var e = entity(status.name());
            var a = ScoreAppealPersistenceMapper.toDomain(e);
            assertThat(a.status()).isEqualTo(status);
            assertThat(a.domainEvents()).isEmpty();
        }
    }

    @Nested @DisplayName("Status-related fields")
    class StatusFields {
        @Test void processingKeepsHandlerId() {
            var e = entity("PROCESSING"); UUID hid = UUID.randomUUID(); e.setHandlerId(hid);
            var a = ScoreAppealPersistenceMapper.toDomain(e);
            assertThat(a.handlerId()).isEqualTo(hid);
        }
        @Test void rejectedKeepsResolution() {
            var e = entity("REJECTED"); e.setResolution("申诉不成立");
            var a = ScoreAppealPersistenceMapper.toDomain(e);
            assertThat(a.resolution()).isEqualTo("申诉不成立");
        }
        @Test void resolvedKeepsFields() {
            var e = entity("RESOLVED"); e.setResolution("已更正"); Instant now = Instant.now(); e.setResolvedAt(now);
            var a = ScoreAppealPersistenceMapper.toDomain(e);
            assertThat(a.resolution()).isEqualTo("已更正");
            assertThat(a.resolvedAt()).isNotNull();
        }
    }

    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test void mapsToEntity() {
            var a = ScoreAppeal.create(new ScoreAppeal.Builder().id(new ScoreAppealId(UUID.randomUUID()))
                    .schoolId(UUID.randomUUID()).scoreAttemptId(UUID.randomUUID())
                    .studentId(UUID.randomUUID()).appealType("SCORE").appealReason("reason"));
            var e = ScoreAppealPersistenceMapper.toEntity(a);
            assertThat(e.getAppealStatus()).isEqualTo("SUBMITTED");
        }
    }

    private ScoreAppealEntity entity(String status) {
        var e = new ScoreAppealEntity();
        e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setScoreAttemptId(UUID.randomUUID()); e.setStudentId(UUID.randomUUID());
        e.setAppealType("SCORE"); e.setAppealReason("reason");
        e.setAppealStatus(status);
        return e;
    }
}
