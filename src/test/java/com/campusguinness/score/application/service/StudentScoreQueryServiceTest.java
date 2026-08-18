package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.query.AuthenticationMembership;
import com.campusguinness.identity.application.query.AuthenticationMembershipQuery;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.StudentScoreListResult;
import com.campusguinness.score.application.query.port.StudentScoreQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentScoreQueryServiceTest {
    private final UUID actorId = UUID.randomUUID();
    private final UUID schoolId = UUID.randomUUID();
    private final StudentScoreQueryPort queryPort = mock(StudentScoreQueryPort.class);
    private final AuthenticationMembershipQuery memberships = mock(AuthenticationMembershipQuery.class);
    private final CurrentActor currentActor = () -> actorId;
    private StudentScoreQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudentScoreQueryService(queryPort, currentActor, memberships);
    }

    @Test
    void listUsesOnlyTheUniqueActiveStudentMembership() {
        when(memberships.findActiveByUserId(actorId)).thenReturn(List.of(
                new AuthenticationMembership(UUID.randomUUID(), schoolId, "STUDENT")));
        var expected = new QueryPage<>(List.of(score()), 0, 20, 1);
        when(queryPort.findVisibleByStudent(actorId, schoolId, 0, 20)).thenReturn(expected);

        assertThat(service.list(0, 20)).isSameAs(expected);
        verify(queryPort).findVisibleByStudent(eq(actorId), eq(schoolId), anyInt(), anyInt());
    }

    @Test
    void listRejectsNoActiveMembership() {
        when(memberships.findActiveByUserId(actorId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.list(0, 20))
                .isInstanceOf(IdentityApplicationException.class)
                .hasMessageContaining("unique active STUDENT membership");
    }

    @Test
    void listRejectsAmbiguousOrWrongRoleMembership() {
        when(memberships.findActiveByUserId(actorId)).thenReturn(List.of(
                new AuthenticationMembership(UUID.randomUUID(), schoolId, "STUDENT"),
                new AuthenticationMembership(UUID.randomUUID(), UUID.randomUUID(), "STUDENT")));
        assertThatThrownBy(() -> service.list(0, 20)).isInstanceOf(IdentityApplicationException.class);

        when(memberships.findActiveByUserId(actorId)).thenReturn(List.of(
                new AuthenticationMembership(UUID.randomUUID(), schoolId, "SCHOOL_ADMIN")));
        assertThatThrownBy(() -> service.list(0, 20)).isInstanceOf(IdentityApplicationException.class);
    }

    @Test
    void detailRequiresTheServerResolvedIdentity() {
        when(memberships.findActiveByUserId(actorId)).thenReturn(List.of(
                new AuthenticationMembership(UUID.randomUUID(), schoolId, "STUDENT")));
        UUID scoreId = UUID.randomUUID();
        var expected = new com.campusguinness.score.application.query.model.StudentScoreDetailResult(
                scoreId, UUID.randomUUID(), UUID.randomUUID(), "活动", "项目", 1,
                "INTEGER", "10", "次", Instant.now(), "APPROVED", UUID.randomUUID(), 1, "规则");
        when(queryPort.findVisibleById(scoreId, actorId, schoolId)).thenReturn(java.util.Optional.of(expected));

        assertThat(service.detail(scoreId)).isSameAs(expected);
    }

    @Test
    void detailHidesOtherStudentAndUnknownScoresAsNotFound() {
        when(memberships.findActiveByUserId(actorId)).thenReturn(List.of(
                new AuthenticationMembership(UUID.randomUUID(), schoolId, "STUDENT")));
        UUID scoreId = UUID.randomUUID();
        when(queryPort.findVisibleById(scoreId, actorId, schoolId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.detail(scoreId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score attempt not found");
    }

    private StudentScoreListResult score() {
        return new StudentScoreListResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "活动", "项目", 1, "INTEGER", "10", "次", Instant.now(), "APPROVED");
    }
}
