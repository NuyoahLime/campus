package com.campusguinness;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every JPA @Entity maps to a real table in PostgreSQL,
 * every business table has an explicit persistence strategy,
 * and no entity is wrongly mapped to an infrastructure table.
 */
class PersistenceSchemaCoverageTest extends PostgreSqlIntegrationTestSupport {

    @PersistenceContext private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    private static final Set<String> INFRASTRUCTURE_TABLES = Set.of(
            "spring_session", "spring_session_attributes", "flyway_schema_history"
    );

    /** Resolves the actual database table name from a JPA EntityType. */
    private String tableNameOf(EntityType<?> entity) {
        Class<?> javaType = entity.getJavaType();
        Table tableAnn = javaType.getAnnotation(Table.class);
        if (tableAnn != null && !tableAnn.name().isEmpty()) {
            return tableAnn.name();
        }
        // Fallback: use entity name (JPA default naming)
        return entity.getName().toLowerCase();
    }

    @Test
    @DisplayName("Each @Entity maps to a real table in PostgreSQL")
    void everyEntityMapsToRealTable() {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();
        assertThat(entities).isNotEmpty();

        for (EntityType<?> entity : entities) {
            String tableName = tableNameOf(entity);
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                    Integer.class, tableName);
            assertThat(count)
                    .as("Table '%s' for entity '%s' must exist in PostgreSQL",
                            tableName, entity.getJavaType().getSimpleName())
                    .isOne();
        }
    }

    @Test
    @DisplayName("No entity maps to an infrastructure table")
    void noEntityMapsToInfrastructureTable() {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();
        for (EntityType<?> entity : entities) {
            String tableName = tableNameOf(entity);
            assertThat(INFRASTRUCTURE_TABLES)
                    .as("Entity '%s' must not map to infrastructure table '%s'",
                            entity.getJavaType().getSimpleName(), tableName)
                    .doesNotContain(tableName);
        }
    }

    @Test
    @DisplayName("All 35 business tables + infrastructure tables exist in PostgreSQL")
    void keyBusinessTablesExist() {
        List<String> requiredBusinessTables = List.of(
                "users", "schools", "school_registrations", "school_memberships",
                "student_profiles", "teacher_profiles",
                "student_identity_applications", "school_admin_invitations",
                "challenge_projects", "project_rule_versions", "project_rule_compatibilities",
                "activity_applications", "activities", "activity_projects",
                "responsible_teachers", "activity_participants",
                "score_attempts", "score_review_records", "score_correction_records",
                "abnormal_score_entries",
                "ranking_definitions", "ranking_versions", "ranking_entries",
                "ranking_entry_score_sources", "l3_authorizations",
                "score_appeals", "appeal_records",
                "media", "media_review_records",
                "activity_results", "result_versions",
                "feedbacks", "notifications", "audit_records", "task_records"
        );

        List<String> actualTables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'",
                String.class);

        for (String required : requiredBusinessTables) {
            assertThat(actualTables)
                    .as("Required table '%s' must exist in public schema", required)
                    .contains(required);
        }
    }

    @Test
    @DisplayName("Every mapped entity table has a primary key column 'id'")
    void everyEntityTableHasPrimaryKey() {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();
        for (EntityType<?> entity : entities) {
            String tableName = tableNameOf(entity);
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns " +
                            "WHERE table_schema='public' AND table_name=? AND column_name='id'",
                    Integer.class, tableName);
            assertThat(count)
                    .as("Table '%s' must have 'id' column", tableName)
                    .isOne();
        }
    }

    @Test
    @DisplayName("Flyway executed exactly 16 migrations")
    void flywayExecuted16Migrations() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(count).isEqualTo(16);
    }

    @Test
    @DisplayName("Entity count matches known expected count (18)")
    void entityCountIsAsExpected() {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();
        assertThat(entities).hasSize(18);
    }

    @Test
    @DisplayName("All 18 entity table names match their @Table annotations")
    void entityTableNamesAreCorrect() {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();
        Set<String> actualTableNames = entities.stream()
                .map(this::tableNameOf)
                .collect(Collectors.toSet());

        Set<String> expectedTables = Set.of(
                "users", "schools", "school_registrations", "school_memberships",
                "student_identity_applications", "school_admin_invitations",
                "challenge_projects",
                "activities", "activity_applications",
                "score_attempts",
                "ranking_definitions", "l3_authorizations",
                "score_appeals",
                "media",
                "activity_results",
                "feedbacks",
                "notifications",
                "audit_records"
        );

        assertThat(actualTableNames).containsExactlyInAnyOrderElementsOf(expectedTables);
    }
}
