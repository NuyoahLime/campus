package com.campusguinness.infrastructure.security.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * One-time SUPER_ADMIN password recovery runner.
 * Activated only with profile "super-admin-credential-recovery"
 * AND property "campus-guinness.security.admin-password-recovery.enabled=true".
 * Runs in non-web mode and exits after completion.
 */
@Component
@Profile("super-admin-credential-recovery")
@ConditionalOnProperty(name = "campus-guinness.security.admin-password-recovery.enabled", havingValue = "true")
public class PasswordRecoveryRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryRunner.class);

    private final PasswordRecoveryService service;
    private final PasswordRecoveryProperties props;
    private final ConfigurableApplicationContext context;

    private int exitCode = 10;

    public PasswordRecoveryRunner(PasswordRecoveryService service, PasswordRecoveryProperties props,
                                   ConfigurableApplicationContext context) {
        this.service = service;
        this.props = props;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!props.isEnabled()) {
                log.info("Password recovery is disabled");
                exitCode = 10;
                return;
            }

            // Validate configuration
            String configError = validateConfig();
            if (configError != null) {
                log.error("Invalid configuration: {}", configError);
                exitCode = 20;
                return;
            }

            var result = service.recover(props);
            exitCode = result.exitCode();

            if (result.success()) {
                log.info("Password recovery successful: {} sessions deleted", result.sessionsDeleted());
            } else {
                log.warn("Password recovery failed with exit code {}", exitCode);
            }
        } catch (Exception e) {
            log.error("Password recovery unexpected error: {}", e.getMessage());
            exitCode = 90;
        } finally {
            SpringApplication.exit(context, this);
        }
    }

    @Override
    public int getExitCode() { return exitCode; }

    private String validateConfig() {
        if (props.getTargetUserId() == null) return "target-user-id is required";
        if (props.getTargetUsername() == null || props.getTargetUsername().isBlank()) return "target-username is required";
        if (props.getExpectedStatus() == null || props.getExpectedStatus().isBlank()) return "expected-status is required";
        if (props.getExpectedPlatformRole() == null || props.getExpectedPlatformRole().isBlank()) return "expected-platform-role is required";
        if (props.getNewPassword() == null || props.getNewPassword().isBlank()) return "new-password is required";
        return null;
    }
}
