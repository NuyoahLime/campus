package com.campusguinness.infrastructure.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class DuplicateUsernameConcurrencyTest extends PostgreSqlIntegrationTestSupport {
    @Autowired AccountProvisioningService svc;
    @Autowired JdbcTemplate jdbc;
    UUID schoolId; UUID actorId; String baseName;

    @BeforeEach void setup() {
        schoolId = UUID.randomUUID(); actorId = UUID.randomUUID();
        baseName = UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,'USCC',?,'INT-'||?,'PRIMARY','Test','Addr','Name','12345','a@b.com','NORMAL')", schoolId, "Concurrency Test School "+baseName, "SC-"+baseName, baseName);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)", actorId, "dupactor-"+baseName, "$2a$10$hAnonDummyHashForTest", "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach void cleanup() {
        String sharedUsername = "dup-" + baseName;
        String actorUsername = "dupactor-" + baseName;
        jdbc.update("DELETE FROM account_provisioning_audit_logs WHERE actor_id=?", actorId);
        jdbc.update("DELETE FROM school_memberships WHERE school_id=?", schoolId);
        jdbc.update("DELETE FROM users WHERE username IN (?,?)", sharedUsername, actorUsername);
        jdbc.update("DELETE FROM schools WHERE id=?", schoolId);
    }

    @Test void concurrentDuplicateUsernameShouldAllowOneWinner() throws Exception {
        String sharedName = "dup-" + baseName;
        var barrier = new CyclicBarrier(2);
        var latch = new CountDownLatch(2);
        var success = new AtomicInteger(0);
        var conflict = new AtomicInteger(0);
        var unexpected = new AtomicInteger(0);
        Runnable task = () -> {
            try { barrier.await(3, TimeUnit.SECONDS); } catch (Exception e) { unexpected.incrementAndGet(); latch.countDown(); return; }
            try {
                svc.createSchoolAdmin(actorId, schoolId, sharedName, "TempPass1!");
                success.incrementAndGet();
            } catch (AccountProvisioningService.DuplicateUsernameException e) {
                conflict.incrementAndGet();
            } catch (Exception e) {
                unexpected.incrementAndGet();
            }
            finally { latch.countDown(); }
        };

        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            executor.submit(task); executor.submit(task);
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("both concurrent tasks completed").isTrue();
            assertThat(success.get()).as("success count").isEqualTo(1);
            assertThat(conflict.get()).as("conflict count").isEqualTo(1);
            assertThat(unexpected.get()).as("unexpected errors").isEqualTo(0);
            Integer userCount = jdbc.queryForObject("SELECT count(*) FROM users WHERE username=?", Integer.class, sharedName);
            assertThat(userCount).isEqualTo(1);
            Integer memCount = jdbc.queryForObject("SELECT count(*) FROM school_memberships WHERE school_id=?", Integer.class, schoolId);
            assertThat(memCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
