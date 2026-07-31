package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.exception.RankingNotFoundException;
import com.campusguinness.ranking.application.exception.StudentRankingAccessException;
import com.campusguinness.ranking.application.query.model.StudentCurrentRankingDetail;
import com.campusguinness.ranking.application.query.model.StudentOwnRanking;
import com.campusguinness.ranking.application.query.model.StudentRankingAvailability;
import com.campusguinness.ranking.application.query.model.StudentRankingEntry;
import com.campusguinness.ranking.application.query.model.StudentRankingProjectItem;
import com.campusguinness.ranking.application.query.model.TiePolicy;
import com.campusguinness.ranking.application.query.port.StudentRankingQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRankingApplicationServiceTest {

    @Mock SchoolMembershipQueryPort membershipQuery;
    @Mock StudentRankingQueryPort rankingQuery;

    private StudentRankingApplicationService service;
    private UUID actorId;
    private UUID activityProjectId;
    private UUID membershipId;

    @BeforeEach
    void setUp() {
        service = new StudentRankingApplicationService(
                membershipQuery, rankingQuery);
        actorId = UUID.randomUUID();
        activityProjectId = UUID.randomUUID();
        membershipId = UUID.randomUUID();
        when(membershipQuery.findActiveStudentMembershipIds(actorId))
                .thenReturn(List.of(membershipId));
    }

    @Test
    void studentCanListAssignedProjectRankings() {
        when(rankingQuery.findRankingProjects(
                actorId, null, null, null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(project(
                        StudentRankingAvailability.CURRENT)), 0, 20, 1));

        var result = service.listProjects(
                actorId, null, null, null, 0, 20);

        assertThat(result.items()).singleElement()
                .extracting(StudentRankingProjectItem::activityProjectId)
                .isEqualTo(activityProjectId);
    }

    @Test
    void studentSeesProjectsAcrossActiveMemberships() {
        when(membershipQuery.findActiveStudentMembershipIds(actorId))
                .thenReturn(List.of(membershipId, UUID.randomUUID()));
        when(rankingQuery.findRankingProjects(
                actorId, null, null, null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(
                        project(StudentRankingAvailability.CURRENT),
                        project(StudentRankingAvailability.NOT_PUBLISHED)),
                        0, 20, 2));

        assertThat(service.listProjects(
                actorId, null, null, null, 0, 20).items()).hasSize(2);
    }

    @Test
    void inactiveStudentMembershipIsExcluded() {
        when(membershipQuery.findActiveStudentMembershipIds(actorId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.listProjects(
                actorId, null, null, null, 0, 20))
                .isInstanceOf(StudentRankingAccessException.class);
        verify(rankingQuery, never()).findRankingProjects(
                any(), any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void unassignedProjectIsExcluded() {
        when(rankingQuery.findRankingProjects(
                actorId, null, null, null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(), 0, 20, 0));

        assertThat(service.listProjects(
                actorId, null, null, null, 0, 20).items()).isEmpty();
    }

    @Test
    void activityParticipantWithoutProjectAssignmentIsExcluded() {
        when(rankingQuery.findRankingProjects(
                actorId, null, null, null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(), 0, 20, 0));

        assertThat(service.listProjects(
                actorId, null, null, null, 0, 20).totalElements()).isZero();
    }

    @Test
    void studentCanReadAssignedCurrentRanking() {
        allowAssignment();
        when(rankingQuery.findAccessibleCurrentRanking(
                actorId, activityProjectId)).thenReturn(Optional.of(detail()));

        assertThat(service.getCurrentRanking(actorId, activityProjectId)
                .versionNumber()).isEqualTo(2);
    }

    @Test
    void studentCannotReadOtherSchoolRanking() {
        when(rankingQuery.existsAccessibleAssignment(
                actorId, activityProjectId)).thenReturn(false);

        assertNotFound(() -> service.getCurrentRanking(
                actorId, activityProjectId));
    }

    @Test
    void studentCannotReadUnassignedSameSchoolRanking() {
        when(rankingQuery.existsAccessibleAssignment(
                actorId, activityProjectId)).thenReturn(false);

        assertNotFound(() -> service.getCurrentRanking(
                actorId, activityProjectId));
    }

    @Test
    void withdrawnRankingLooksNotFound() {
        allowAssignment();
        when(rankingQuery.findAccessibleCurrentRanking(
                actorId, activityProjectId)).thenReturn(Optional.empty());

        assertNotFound(() -> service.getCurrentRanking(
                actorId, activityProjectId));
    }

    @Test
    void replacedVersionIsNotCurrent() {
        allowAssignment();
        when(rankingQuery.findAccessibleCurrentRanking(
                actorId, activityProjectId)).thenReturn(Optional.of(detail()));

        assertThat(service.getCurrentRanking(actorId, activityProjectId)
                .versionNumber()).isEqualTo(2);
    }

    @Test
    void currentPointerSelectsPublishedVersion() {
        allowAssignment();
        when(rankingQuery.findAccessibleCurrentRanking(
                actorId, activityProjectId)).thenReturn(Optional.of(detail()));

        assertThat(service.getCurrentRanking(actorId, activityProjectId)
                .publishedAt()).isEqualTo(Instant.parse("2026-07-30T09:00:00Z"));
    }

    @Test
    void rankingEntriesComeFromSnapshot() {
        allowAssignment();
        when(rankingQuery.findAccessibleCurrentRanking(
                actorId, activityProjectId)).thenReturn(Optional.of(detail()));

        assertThat(service.getCurrentRanking(actorId, activityProjectId).entries())
                .singleElement()
                .extracting(StudentRankingEntry::studentDisplayName)
                .isEqualTo("Snapshot Student");
    }

    @Test
    void studentOwnEntryIsMarked() {
        allowAssignment();
        when(rankingQuery.findAccessibleCurrentRanking(
                actorId, activityProjectId)).thenReturn(Optional.of(detail()));

        assertThat(service.getCurrentRanking(actorId, activityProjectId).entries())
                .singleElement()
                .extracting(StudentRankingEntry::isCurrentStudent)
                .isEqualTo(true);
    }

    @Test
    void studentWithoutEntryGetsNoOwnRank() {
        allowAssignment();
        when(rankingQuery.findOwnCurrentRanking(
                actorId, activityProjectId)).thenReturn(Optional.empty());

        assertNotFound(() -> service.getMyRank(actorId, activityProjectId));
    }

    @Test
    void disabledProjectIsListedAsDisabled() {
        when(rankingQuery.findRankingProjects(
                actorId, null, "DISABLED", null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(
                        project(StudentRankingAvailability.DISABLED)),
                        0, 20, 1));

        assertThat(service.listProjects(
                actorId, null, "DISABLED", null, 0, 20).items())
                .singleElement()
                .extracting(StudentRankingProjectItem::rankingAvailability)
                .isEqualTo(StudentRankingAvailability.DISABLED);
    }

    @Test
    void publicL1AccessIsRejected() {
        when(rankingQuery.findRankingProjects(
                actorId, null, null, null, 0, 20))
                .thenReturn(new QueryPage<>(List.of(), 0, 20, 0));

        assertThat(service.listProjects(
                actorId, null, null, null, 0, 20).items()).isEmpty();
        assertThat(List.of(
                StudentRankingApplicationService.class.getDeclaredMethods()))
                .noneMatch(method -> method.getName().toLowerCase().contains("public"));
    }

    private void allowAssignment() {
        when(rankingQuery.existsAccessibleAssignment(
                actorId, activityProjectId)).thenReturn(true);
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(RankingNotFoundException.class)
                .hasMessage("Ranking not found");
    }

    private StudentRankingProjectItem project(
            StudentRankingAvailability availability) {
        return new StudentRankingProjectItem(
                activityProjectId,
                UUID.randomUUID(),
                "Activity",
                UUID.randomUUID(),
                "School",
                "ENDED",
                UUID.randomUUID(),
                "Project",
                "INTEGER",
                "points",
                availability == StudentRankingAvailability.DISABLED
                        ? "NO_RANKING" : "HIGHER_BETTER",
                availability,
                availability == StudentRankingAvailability.CURRENT ? 2 : null,
                availability == StudentRankingAvailability.CURRENT
                        ? Instant.parse("2026-07-30T09:00:00Z") : null,
                availability == StudentRankingAvailability.CURRENT ? 1L : null,
                availability == StudentRankingAvailability.CURRENT ? 1 : null,
                availability == StudentRankingAvailability.CURRENT ? "100" : null);
    }

    private StudentCurrentRankingDetail detail() {
        return new StudentCurrentRankingDetail(
                activityProjectId,
                UUID.randomUUID(),
                "Activity",
                "School",
                UUID.randomUUID(),
                "Project",
                "INTEGER",
                "points",
                "HIGHER_BETTER",
                "BEST",
                TiePolicy.COMPETITION,
                2,
                Instant.parse("2026-07-30T09:00:00Z"),
                1,
                1,
                "100",
                List.of(new StudentRankingEntry(
                        1, "Snapshot Student", "100", true)));
    }
}
