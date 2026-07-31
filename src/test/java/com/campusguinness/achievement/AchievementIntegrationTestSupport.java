package com.campusguinness.achievement;

import com.campusguinness.achievement.application.query.model.AchievementIssueResult;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import com.campusguinness.achievement.application.service.SchoolAdminAchievementApplicationService;
import com.campusguinness.ranking.RankingIntegrationTestSupport;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import com.campusguinness.ranking.application.service.SchoolAdminRankingApplicationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

public abstract class AchievementIntegrationTestSupport
        extends RankingIntegrationTestSupport {

    @Autowired
    protected SchoolAdminRankingApplicationService rankingService;

    @Autowired
    protected SchoolAdminAchievementApplicationService achievementService;

    protected RankingVersionDetail publishRanking() {
        String fingerprint = rankingService
                .preview(adminId, activityProjectId)
                .sourceFingerprint();
        return rankingService.publish(adminId, activityProjectId, fingerprint);
    }

    protected UUID firstEntryId(RankingVersionDetail version) {
        return version.entries().getFirst().rankingEntryId();
    }

    protected AchievementIssueResult issueFirst(
            RankingVersionDetail version) {
        return achievementService.issue(adminId, firstEntryId(version));
    }

    protected SchoolAdminAchievementDetail issueRecord(
            RankingVersionDetail version) {
        return issueFirst(version).record();
    }
}
