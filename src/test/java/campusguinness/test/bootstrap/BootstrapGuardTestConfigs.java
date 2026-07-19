package campusguinness.test.bootstrap;

import com.campusguinness.identity.application.service.SuperAdminBootstrapService;
import com.campusguinness.infrastructure.bootstrap.BootstrapAdminProperties;
import com.campusguinness.infrastructure.bootstrap.SuperAdminBootstrapRunner;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.mock;

/**
 * Test configuration for SuperAdminBootstrapRunnerGuardTest.
 * <p>
 * Uses {@code @Import(SuperAdminBootstrapRunner.class)} to register the
 * <b>real production</b> runner so that conditional bean registration
 * ({@code @Profile} + {@code @ConditionalOnProperty}) is exercised on
 * the actual production class — not a test subclass or no-op replacement.
 * <p>
 * Placed outside {@code com.campusguinness} so that the main
 * {@code @SpringBootApplication} component scan does not pick up this
 * configuration and cause bean conflicts in integration tests.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BootstrapAdminProperties.class)
@Import(SuperAdminBootstrapRunner.class)
public class BootstrapGuardTestConfigs {

    @Bean
    SuperAdminBootstrapService superAdminBootstrapService() {
        return mock(SuperAdminBootstrapService.class);
    }
}
