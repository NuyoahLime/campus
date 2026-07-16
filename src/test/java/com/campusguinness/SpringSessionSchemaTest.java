package com.campusguinness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SpringSessionSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void springSessionTableExists() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='spring_session'", Integer.class);
        assertThat(count).isOne();
    }

    @Test
    void springSessionAttributesTableExists() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='spring_session_attributes'", Integer.class);
        assertThat(count).isOne();
    }
}
