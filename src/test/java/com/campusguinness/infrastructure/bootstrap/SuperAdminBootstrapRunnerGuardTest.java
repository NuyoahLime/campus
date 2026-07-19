package com.campusguinness.infrastructure.bootstrap;

import campusguinness.test.bootstrap.BootstrapGuardTestConfigs;

import com.campusguinness.identity.application.service.SuperAdminBootstrapService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies SuperAdminBootstrapRunner is only activated when BOTH
 * the "bootstrap-admin" profile AND the enabled property are present.
 * <p>
 * Uses a single {@code @Configuration} class that imports the
 * <b>real production</b> {@link SuperAdminBootstrapRunner} via
 * {@code @Import}. No test subclasses or no-op replacements are used.
 * <p>
 * {@link ApplicationContextRunner} does not call
 * {@code ApplicationRunner.run()} — it only refreshes the context,
 * so {@code SpringApplication.exit()} is never invoked and the
 * context stays alive for assertions.
 */
class SuperAdminBootstrapRunnerGuardTest {

    static final String PROFILE = "spring.profiles.active=bootstrap-admin";
    static final String ENABLED_TRUE = "campus-guinness.bootstrap-admin.enabled=true";
    static final String ENABLED_FALSE = "campus-guinness.bootstrap-admin.enabled=false";
    static final String USERNAME = "campus-guinness.bootstrap-admin.username=test-bootstrap-admin";
    static final String PASSWORD = "campus-guinness.bootstrap-admin.password=TestBootstrap123";

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(BootstrapGuardTestConfigs.class);
    }

    // ── Negative: runner must NOT be present ──

    @Test
    void runnerNotPresentWithoutProfileWithoutProperty() {
        runner().run(ctx ->
                assertThat(ctx.getBeansOfType(SuperAdminBootstrapRunner.class)).isEmpty());
    }

    @Test
    void runnerNotPresentWithPropertyWithoutProfile() {
        runner()
                .withPropertyValues(ENABLED_TRUE)
                .run(ctx -> assertThat(ctx.getBeansOfType(SuperAdminBootstrapRunner.class)).isEmpty());
    }

    @Test
    void runnerNotPresentWithProfileWithoutProperty() {
        runner()
                .withPropertyValues(PROFILE)
                .run(ctx -> assertThat(ctx.getBeansOfType(SuperAdminBootstrapRunner.class)).isEmpty());
    }

    @Test
    void runnerNotPresentWithProfileAndDisabledProperty() {
        runner()
                .withPropertyValues(PROFILE, ENABLED_FALSE)
                .run(ctx -> assertThat(ctx.getBeansOfType(SuperAdminBootstrapRunner.class)).isEmpty());
    }

    // ── Positive: both conditions met → real production bean exists ──

    @Test
    void runnerPresentWithProfileAndEnabledProperty() {
        runner()
                .withPropertyValues(PROFILE, ENABLED_TRUE, USERNAME, PASSWORD)
                .run(ctx -> {
                    // Real production runner bean exists
                    assertThat(ctx.getBeansOfType(SuperAdminBootstrapRunner.class)).hasSize(1);
                    SuperAdminBootstrapRunner bean =
                            ctx.getBean(SuperAdminBootstrapRunner.class);
                    assertThat(bean).isExactlyInstanceOf(SuperAdminBootstrapRunner.class);

                    // BootstrapAdminProperties correctly bound
                    BootstrapAdminProperties props =
                            ctx.getBean(BootstrapAdminProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getUsername()).isEqualTo("test-bootstrap-admin");
                    assertThat(props.getPassword()).isEqualTo("TestBootstrap123");

                    // Context remains alive — no exit triggered
                    assertThat(ctx.isActive()).isTrue();
                });
    }

    @Test
    void enabledContextDoesNotTriggerBootstrapOrExit() {
        runner()
                .withPropertyValues(PROFILE, ENABLED_TRUE, USERNAME, PASSWORD)
                .run(ctx -> {
                    // Runner bean present
                    assertThat(ctx.getBeansOfType(SuperAdminBootstrapRunner.class)).hasSize(1);

                    // Context alive
                    assertThat(ctx.isActive()).isTrue();

                    // Bootstrap service never called — no bootstrap executed
                    SuperAdminBootstrapService svc =
                            ctx.getBean(SuperAdminBootstrapService.class);
                    verifyNoInteractions(svc);
                });
    }
}
