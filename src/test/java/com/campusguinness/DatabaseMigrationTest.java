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
        assertThat(count).isEqualTo(24);
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
}
