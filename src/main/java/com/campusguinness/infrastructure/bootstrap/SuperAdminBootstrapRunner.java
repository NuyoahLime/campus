package com.campusguinness.infrastructure.bootstrap;

import com.campusguinness.identity.application.service.BootstrapRefusedException;
import com.campusguinness.identity.application.service.SuperAdminBootstrapService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * One-time SUPER_ADMIN bootstrap runner.
 * <p>
 * Activated only when BOTH conditions are met:
 * <ul>
 *   <li>Spring profile "bootstrap-admin" is active</li>
 *   <li>Property "campus-guinness.bootstrap-admin.enabled" is "true"</li>
 * </ul>
 * <p>
 * Credentials are read exclusively from environment variables.
 * The runner exits the JVM after completion — no web server is started.
 */
@Component
@Profile("bootstrap-admin")
@ConditionalOnProperty(name = "campus-guinness.bootstrap-admin.enabled", havingValue = "true")
public class SuperAdminBootstrapRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

    private final SuperAdminBootstrapService service;
    private final BootstrapAdminProperties props;
    private final ConfigurableApplicationContext context;

    private int exitCode = 1;

    public SuperAdminBootstrapRunner(SuperAdminBootstrapService service,
                                      BootstrapAdminProperties props,
                                      ConfigurableApplicationContext context) {
        this.service = service;
        this.props = props;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Validate configuration
            if (props.getUsername() == null || props.getUsername().trim().isEmpty()) {
                log.error("CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_USERNAME is required");
                exitCode = 2;
                return;
            }
            if (props.getPassword() == null || props.getPassword().isEmpty()) {
                log.error("CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_PASSWORD is required");
                exitCode = 3;
                return;
            }

            var result = service.bootstrap(props.getUsername(), props.getPassword());

            log.info("SUPER_ADMIN created: userId={}, username={}, status={}, role={}",
                    result.userId(), result.username(), result.status(), result.platformRole());
            exitCode = 0;

        } catch (BootstrapRefusedException e) {
            log.warn("Bootstrap refused: {}", e.getMessage());
            exitCode = 4;
        } catch (Exception e) {
            log.error("Bootstrap failed: {}", e.getMessage());
            exitCode = 5;
        } finally {
            // Shut down the context so the JVM exits
            SpringApplication.exit(context, this);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
