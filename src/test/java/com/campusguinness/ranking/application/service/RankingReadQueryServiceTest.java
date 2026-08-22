package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.StudentSchoolScope;
import com.campusguinness.identity.application.service.StudentSchoolScopeAuthorization;
import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadSummaryResult;
import com.campusguinness.ranking.application.query.port.RankingReadQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingReadQueryServiceTest {
    @Mock RankingReadQueryPort queryPort;
    @Mock CurrentActor currentActor;
    @Mock StudentSchoolScopeAuthorization studentScope;
    @Mock SchoolResourceAuthorization schoolAuthorization;

    private RankingReadQueryService service;

    @BeforeEach
    void setUp() {
        service = new RankingReadQueryService(queryPort, currentActor, studentScope, schoolAuthorization);
    }

    @Test
    void publicListUsesOnlyPublishedVisibilityWithoutSchoolScope() {
        var expected = new QueryPage<RankingReadSummaryResult>(List.of(), 0, 20, 0);
        when(queryPort.list(null, false, 0, 20)).thenReturn(expected);

        assertThat(service.listPublic(0, 20)).isSameAs(expected);

        verify(queryPort).list(null, false, 0, 20);
    }

    @Test
    void studentListUsesTheServerResolvedSchoolAndIncludesGlobalRankings() {
        UUID schoolId = UUID.randomUUID();
        var expected = new QueryPage<RankingReadSummaryResult>(List.of(), 0, 20, 0);
        when(studentScope.requireUniqueActiveStudent()).thenReturn(new StudentSchoolScope(UUID.randomUUID(), schoolId));
        when(queryPort.list(schoolId, true, 0, 20)).thenReturn(expected);

        assertThat(service.listStudent(0, 20)).isSameAs(expected);

        verify(queryPort).list(schoolId, true, 0, 20);
    }

    @Test
    void studentDetailHidesOtherSchoolRankings() {
        UUID schoolId = UUID.randomUUID();
        UUID rankingId = UUID.randomUUID();
        when(studentScope.requireUniqueActiveStudent()).thenReturn(new StudentSchoolScope(UUID.randomUUID(), schoolId));
        when(queryPort.detail(rankingId, schoolId, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.studentDetail(rankingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ranking not found");

        verify(queryPort).detail(rankingId, schoolId, true);
    }

    @Test
    void schoolAdminListUsesOnlyTheServerResolvedSchool() {
        UUID schoolId = UUID.randomUUID();
        var expected = new QueryPage<RankingReadSummaryResult>(List.of(), 0, 20, 0);
        when(schoolAuthorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        when(queryPort.list(schoolId, false, 0, 20)).thenReturn(expected);

        assertThat(service.listSchoolAdmin(0, 20)).isSameAs(expected);

        verify(queryPort).list(schoolId, false, 0, 20);
    }

    @Test
    void schoolAdminDetailHidesAnotherSchoolsRanking() {
        UUID schoolId = UUID.randomUUID();
        UUID rankingId = UUID.randomUUID();
        when(schoolAuthorization.requireUniqueSchoolAdminSchool()).thenReturn(schoolId);
        when(queryPort.detail(rankingId, schoolId, false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.schoolAdminDetail(rankingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ranking not found");

        verify(queryPort).detail(rankingId, schoolId, false);
    }

    @Test
    void rejectsInvalidPaginationBeforeReading() {
        assertThatThrownBy(() -> service.listPublic(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listPublic(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
