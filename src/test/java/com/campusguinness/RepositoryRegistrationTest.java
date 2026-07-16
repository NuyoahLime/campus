package com.campusguinness;

import com.campusguinness.activity.internal.persistence.ActivityApplicationJpaRepository;
import com.campusguinness.activity.internal.persistence.ActivityJpaRepository;
import com.campusguinness.appeal.internal.persistence.ScoreAppealJpaRepository;
import com.campusguinness.feedback.internal.persistence.FeedbackJpaRepository;
import com.campusguinness.identity.internal.persistence.UserJpaRepository;
import com.campusguinness.media.internal.persistence.MediaJpaRepository;
import com.campusguinness.project.internal.persistence.ChallengeProjectJpaRepository;
import com.campusguinness.ranking.internal.persistence.L3AuthorizationJpaRepository;
import com.campusguinness.ranking.internal.persistence.RankingDefinitionJpaRepository;
import com.campusguinness.result.internal.persistence.ActivityResultJpaRepository;
import com.campusguinness.school.internal.persistence.SchoolRegistrationJpaRepository;
import com.campusguinness.school.internal.persistence.SchoolJpaRepository;
import com.campusguinness.score.internal.persistence.ScoreAttemptJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RepositoryRegistrationTest {

    @Autowired private ApplicationContext ctx;

    @Test void userRepositoryExists() { assertThat(ctx.getBean(UserJpaRepository.class)).isNotNull(); }
    @Test void schoolRepositoryExists() { assertThat(ctx.getBean(SchoolJpaRepository.class)).isNotNull(); }
    @Test void schoolRegistrationRepositoryExists() { assertThat(ctx.getBean(SchoolRegistrationJpaRepository.class)).isNotNull(); }
    @Test void challengeProjectRepositoryExists() { assertThat(ctx.getBean(ChallengeProjectJpaRepository.class)).isNotNull(); }
    @Test void activityApplicationRepositoryExists() { assertThat(ctx.getBean(ActivityApplicationJpaRepository.class)).isNotNull(); }
    @Test void activityRepositoryExists() { assertThat(ctx.getBean(ActivityJpaRepository.class)).isNotNull(); }
    @Test void scoreAttemptRepositoryExists() { assertThat(ctx.getBean(ScoreAttemptJpaRepository.class)).isNotNull(); }
    @Test void rankingDefinitionRepositoryExists() { assertThat(ctx.getBean(RankingDefinitionJpaRepository.class)).isNotNull(); }
    @Test void l3AuthorizationRepositoryExists() { assertThat(ctx.getBean(L3AuthorizationJpaRepository.class)).isNotNull(); }
    @Test void scoreAppealRepositoryExists() { assertThat(ctx.getBean(ScoreAppealJpaRepository.class)).isNotNull(); }
    @Test void mediaRepositoryExists() { assertThat(ctx.getBean(MediaJpaRepository.class)).isNotNull(); }
    @Test void activityResultRepositoryExists() { assertThat(ctx.getBean(ActivityResultJpaRepository.class)).isNotNull(); }
    @Test void feedbackRepositoryExists() { assertThat(ctx.getBean(FeedbackJpaRepository.class)).isNotNull(); }
}
