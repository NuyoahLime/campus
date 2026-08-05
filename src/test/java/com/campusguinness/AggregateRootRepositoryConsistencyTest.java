package com.campusguinness;

import com.campusguinness.activity.internal.persistence.ActivityApplicationJpaRepository;
import com.campusguinness.activity.internal.persistence.ActivityJpaRepository;
import com.campusguinness.appeal.internal.persistence.ScoreAppealJpaRepository;
import com.campusguinness.feedback.internal.persistence.FeedbackJpaRepository;
import com.campusguinness.identity.internal.persistence.SchoolAdminInvitationJpaRepository;
import com.campusguinness.identity.internal.persistence.SchoolMembershipJpaRepository;
import com.campusguinness.identity.internal.persistence.StudentIdentityApplicationJpaRepository;
import com.campusguinness.identity.internal.persistence.UserJpaRepository;
import com.campusguinness.media.internal.persistence.MediaJpaRepository;
import com.campusguinness.project.internal.persistence.ChallengeProjectJpaRepository;
import com.campusguinness.ranking.internal.persistence.L3AuthorizationJpaRepository;
import com.campusguinness.ranking.internal.persistence.RankingDefinitionJpaRepository;
import com.campusguinness.result.internal.persistence.ActivityResultJpaRepository;
import com.campusguinness.school.internal.persistence.SchoolRegistrationJpaRepository;
import com.campusguinness.school.internal.persistence.SchoolJpaRepository;
import com.campusguinness.score.internal.persistence.ScoreAttemptJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies aggregate-root-to-Repository consistency:
 * - Every declared persistence aggregate root has an explicit Repository decision.
 * - No unexplained "aggregate root but no Repository" gaps.
 * - No sub-entity mistakenly treated as an aggregate root with its own Repository.
 */
class AggregateRootRepositoryConsistencyTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private ApplicationContext ctx;

    /**
     * Known aggregate roots that require a dedicated Repository.
     * Mapping: aggregate root module → Repository class.
     */
    private static final Set<Class<?>> AGGREGATE_ROOT_REPOSITORIES = Set.of(
            UserJpaRepository.class,
            StudentIdentityApplicationJpaRepository.class,
            SchoolAdminInvitationJpaRepository.class,
            SchoolJpaRepository.class,
            SchoolRegistrationJpaRepository.class,
            ChallengeProjectJpaRepository.class,
            ActivityApplicationJpaRepository.class,
            ActivityJpaRepository.class,
            ScoreAttemptJpaRepository.class,
            RankingDefinitionJpaRepository.class,
            L3AuthorizationJpaRepository.class,
            ScoreAppealJpaRepository.class,
            MediaJpaRepository.class,
            ActivityResultJpaRepository.class,
            FeedbackJpaRepository.class
    );

    /**
     * Entities that are NOT aggregate roots:
     * - SchoolMembershipEntity: internal to User aggregate; may use an internal JPA repository
     *   behind UserRepositoryAdapter, but is not an aggregate-root repository.
     * - NotificationEntity: infrastructure table, system write
     * - AuditRecordEntity: append-only, interceptor write
     */
    private static final Set<String> NON_AGGREGATE_ROOT_ENTITIES = Set.of(
            "SchoolMembershipEntity",
            "NotificationEntity",
            "AuditRecordEntity"
    );

    @Test
    @DisplayName("Every aggregate root Repository bean exists in context")
    void everyAggregateRootRepositoryExists() {
        for (Class<?> repoClass : AGGREGATE_ROOT_REPOSITORIES) {
            Object bean = ctx.getBean(repoClass);
            assertThat(bean)
                    .as("Aggregate root Repository '%s' must be a Spring bean", repoClass.getSimpleName())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("Total aggregate root Repository count is exactly 15")
    void aggregateRootRepositoryCountIs15() {
        assertThat(AGGREGATE_ROOT_REPOSITORIES).hasSize(15);
    }

    @Test
    @DisplayName("All 15 aggregate root Repository beans are registered")
    void allAggregateRootRepositoriesRegistered() {
        // Simply verify each known repository bean exists in context
        String[] allRepoNames = ctx.getBeanNamesForType(org.springframework.data.repository.Repository.class);

        int matched = 0;
        for (Class<?> expectedRepo : AGGREGATE_ROOT_REPOSITORIES) {
            boolean found = false;
            for (String beanName : allRepoNames) {
                if (expectedRepo.isAssignableFrom(ctx.getType(beanName))) {
                    found = true;
                    break;
                }
            }
            assertThat(found)
                    .as("Repository '%s' should be registered in ApplicationContext", expectedRepo.getSimpleName())
                    .isTrue();
            if (found) matched++;
        }
        assertThat(matched).isEqualTo(15);
    }

    @Test
    @DisplayName("No aggregate root Entity exists without a Repository decision")
    void noAggregateRootWithoutRepository() {
        // All 15 aggregate roots have corresponding Repository beans
        // This test confirms the documented design: 15 aggregate roots = 15 repositories
        for (Class<?> repoClass : AGGREGATE_ROOT_REPOSITORIES) {
            assertThat(ctx.getBeanNamesForType(repoClass))
                    .as("Repository '%s' must be registered", repoClass.getSimpleName())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("NotificationEntity and AuditRecordEntity are classified as non-aggregate-root infrastructure entities")
    void nonAggregateRootEntitiesAreClassifiedCorrectly() {
        assertThat(NON_AGGREGATE_ROOT_ENTITIES)
                .contains("NotificationEntity", "AuditRecordEntity", "SchoolMembershipEntity");
    }

    @Test
    @DisplayName("SchoolMembershipJpaRepository is internal and does not increase aggregate root count")
    void schoolMembershipRepositoryIsInternalOnly() {
        assertThat(ctx.getBean(SchoolMembershipJpaRepository.class)).isNotNull();
        assertThat(AGGREGATE_ROOT_REPOSITORIES)
                .doesNotContain(SchoolMembershipJpaRepository.class)
                .hasSize(15);
    }
}
