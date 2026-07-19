package com.campusguinness;

import com.campusguinness.infrastructure.bootstrap.BootstrapAdminProperties;
import com.campusguinness.infrastructure.security.SecurityCorsProperties;
import com.campusguinness.infrastructure.security.recovery.PasswordRecoveryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BootstrapAdminProperties.class, SecurityCorsProperties.class, PasswordRecoveryProperties.class})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
