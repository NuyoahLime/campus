package com.campusguinness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConfigurationSafetyTest {

    @Autowired
    private Environment env;

    @Test
    void hibernateAutoDdlIsDisabled() {
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
    }

    @Test
    void openInViewIsDisabled() {
        assertThat(env.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void sessionSchemaAutoInitIsDisabled() {
        assertThat(env.getProperty("spring.session.jdbc.initialize-schema")).isEqualTo("never");
    }

    @Test
    void sqlInitModeIsNever() {
        assertThat(env.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }
}
