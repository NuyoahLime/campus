package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.query.port.SchoolAdminRankingQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StudentRankingWithdrawalRegressionIT extends RankingIntegrationTestSupport {

    @Autowired SchoolAdminRankingApplicationService managementService;
    @Autowired StudentRankingService studentRankingService;
    @Autowired SchoolAdminRankingQueryPort rankingQuery;
    @Autowired MockMvc mockMvc;

    @Test
    void studentReadsCurrentPublishedVersion() {
        var published = publish();

        var result = studentRankingService.getCurrentRanking(
                activityProjectId, studentId).orElseThrow();

        assertThat(result.version()).isEqualTo(published.versionNumber());
        assertThat(result.entries()).singleElement()
                .extracting(entry -> entry.isCurrentStudent())
                .isEqualTo(true);
    }

    @Test
    void studentDoesNotReadReplacedVersion() {
        var first = publish();
        var second = publish();

        var result = studentRankingService.getCurrentRanking(
                activityProjectId, studentId).orElseThrow();

        assertThat(result.version()).isEqualTo(second.versionNumber());
        assertThat(result.version()).isNotEqualTo(first.versionNumber());
    }

    @Test
    void studentDoesNotReadWithdrawnVersion() {
        publish();
        managementService.withdraw(adminId, activityProjectId, "reason");

        assertThat(studentRankingService.getCurrentRanking(
                activityProjectId, studentId)).isEmpty();
    }

    @Test
    void publicDoesNotReadWithdrawnVersion() throws Exception {
        publish();
        managementService.withdraw(adminId, activityProjectId, "reason");

        mockMvc.perform(get(
                        "/api/v1/public/activity-projects/{id}/ranking",
                        activityProjectId))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentOwnRankUsesCurrentVersionPointer() {
        publish();
        var second = publish();

        var own = studentRankingService.getMyRank(
                activityProjectId, studentId).orElseThrow();

        assertThat(own.version()).isEqualTo(second.versionNumber());
        assertThat(own.rank()).isEqualTo(1);
    }

    @Test
    void historicalVersionRemainsQueryableBySchoolAdmin() {
        var published = publish();
        managementService.withdraw(adminId, activityProjectId, "reason");

        assertThat(rankingQuery.findVersion(
                schoolId, published.versionId())).isPresent();
        assertThat(rankingQuery.findVersion(
                schoolId, published.versionId()).orElseThrow().entries())
                .hasSize(1);
    }

    private com.campusguinness.ranking.application.query.model.RankingVersionDetail publish() {
        String fingerprint = managementService.preview(
                adminId, activityProjectId).sourceFingerprint();
        return managementService.publish(
                adminId, activityProjectId, fingerprint);
    }
}
