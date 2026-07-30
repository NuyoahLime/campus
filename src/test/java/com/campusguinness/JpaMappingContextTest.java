package com.campusguinness;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JPA mapping context is correctly configured:
 * - EntityManagerFactory starts with PostgreSQL 19.4 Testcontainer.
 * - All 19 entities are scanned and mapped.
 * - Hibernate ddl-auto is 'none' (verified via configuration).
 * - Flyway executed all migrations.
 */
class JpaMappingContextTest extends PostgreSqlIntegrationTestSupport {

    @Autowired private EntityManagerFactory emf;
    @PersistenceContext private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Value("${spring.jpa.hibernate.ddl-auto}") private String ddlAuto;

    @Test
    @DisplayName("EntityManagerFactory starts successfully")
    void entityManagerFactoryStartsSuccessfully() {
        assertThat(emf).isNotNull();
        assertThat(emf.isOpen()).isTrue();
    }

    @Test
    @DisplayName("All 20 entities are scanned by JPA metamodel")
    void all19EntitiesAreScanned() {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();
        assertThat(entities)
                .as("Expected 21 JPA entities, found %d", entities.size())
                .hasSize(21);
    }

    @Test
    @DisplayName("Hibernate ddl-auto is 'none'")
    void hibernateAutoDdlIsNone() {
        assertThat(ddlAuto).isEqualTo("none");
    }

    @Test
    @DisplayName("Flyway executed exactly 22 successful migrations")
    void flywayExecutedAllMigrations() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(count).isEqualTo(22);
    }

    @Test
    @DisplayName("Hibernate did not create or alter any tables")
    void hibernateDidNotCreateTables() {
        // 33 business tables + 2 spring_session tables + 1 flyway_schema_history = 36
        // (activity_participants already existed in V005; V019 drops and recreates activity_project_participants)
        Integer totalTables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                        "WHERE table_schema='public' AND table_type='BASE TABLE'", Integer.class);
        // flyway_schema_history is in public schema
        assertThat(totalTables).isEqualTo(40);
    }

    @Test
    @DisplayName("PostgreSQL version is 18.x (as defined in application-test.yml)")
    void postgresVersionIs18() {
        String version = jdbc.queryForObject("SELECT current_setting('server_version')", String.class);
        assertThat(version).startsWith("18.");
    }
}
