package com.campusguinness.activity.internal.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityParticipantTest {
    @Test
    void assignmentCreatesTheMinimalParticipantSnapshot() {
        UUID activityId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-23T04:00:00Z");

        ActivityParticipant participant = ActivityParticipant.assign(activityId, membershipId, createdAt);

        assertThat(participant.id()).isNotNull();
        assertThat(participant.activityId()).isEqualTo(activityId);
        assertThat(participant.studentMembershipId()).isEqualTo(membershipId);
        assertThat(participant.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void rejectsMissingAssignmentIdentity() {
        assertThatThrownBy(() -> ActivityParticipant.assign(null, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activityId required");
        assertThatThrownBy(() -> ActivityParticipant.assign(UUID.randomUUID(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("studentMembershipId required");
    }
}
