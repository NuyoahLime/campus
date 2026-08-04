package com.campusguinness;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayMigrationsExecuted() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(count).isEqualTo(26);
    }

    @Test
    void allMigrationsSuccessful() {
        Integer failed = jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        assertThat(failed).isZero();
    }

    @Test
    void achievementRecordHardeningIsPresent() {
        Integer snapshotColumns = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema='public'
                  AND table_name='achievement_records'
                  AND column_name IN (
                    'school_name_snapshot',
                    'activity_title_snapshot',
                    'project_name_snapshot',
                    'ranking_version_number_snapshot')
                  AND is_nullable='NO'
                """, Integer.class);
        assertThat(snapshotColumns).isEqualTo(4);

        Integer uniqueEntryIndex = jdbc.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname='public'
                  AND tablename='achievement_records'
                  AND indexname='uq_achievement_record_ranking_entry'
                """, Integer.class);
        assertThat(uniqueEntryIndex).isOne();
    }

    @Test
    void verifiedEmailAndTokenInfrastructureIsPresent() {
        Integer userColumns = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema='public'
                  AND table_name='users'
                  AND column_name IN (
                    'email',
                    'email_normalized',
                    'email_verified_at',
                    'registration_source')
                """, Integer.class);
        assertThat(userColumns).isEqualTo(4);

        Integer tokenTables = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema='public'
                  AND table_type='BASE TABLE'
                  AND table_name IN (
                    'email_verification_tokens',
                    'password_reset_tokens')
                """, Integer.class);
        assertThat(tokenTables).isEqualTo(2);

        Integer emailIndex = jdbc.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname='public'
                  AND tablename='users'
                  AND indexname='uq_users_email_normalized'
                  AND indexdef ILIKE '%WHERE (email_normalized IS NOT NULL)%'
                """, Integer.class);
        assertThat(emailIndex).isOne();

        Integer tokenHashConstraints = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.table_constraints
                WHERE table_schema='public'
                  AND constraint_type='UNIQUE'
                  AND constraint_name IN (
                    'uq_email_verification_token_hash',
                    'uq_password_reset_token_hash')
                """, Integer.class);
        assertThat(tokenHashConstraints).isEqualTo(2);

        Integer tokenForeignKeys = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.table_constraints
                WHERE table_schema='public'
                  AND constraint_type='FOREIGN KEY'
                  AND constraint_name IN (
                    'fk_email_verification_token_user',
                    'fk_password_reset_token_user')
                """, Integer.class);
        assertThat(tokenForeignKeys).isEqualTo(2);
    }
}
