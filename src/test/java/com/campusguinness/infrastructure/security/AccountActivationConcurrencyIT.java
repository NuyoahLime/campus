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
class AccountActivationConcurrencyIT extends PostgreSqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    UUID userId; String username; String correctHash;

    @BeforeEach void setup() {
        userId = UUID.randomUUID(); username = "cc-" + UUID.randomUUID().toString().substring(0,8);
        // BCrypt hash of "temp123" — matches test password below
        correctHash = "$2a$10$DiqXqAqsCySLGxKHiRQbkeIzM0aKdhAtmJJ5pGScXkCUXF1H/Y5hy";
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,activation_issued_at,activation_expires_at) VALUES (?,?,?,?,now(),now() + INTERVAL '72 hours')", userId, username, correctHash, "PENDING_ACTIVATION");
    }

    @AfterEach void cleanup() { jdbc.update("DELETE FROM users WHERE id=?", userId); }

    @Test void atomicConditionUpdatePreventsDoubleActivation() throws Exception {
        var barrier = new CyclicBarrier(2);
        var latch = new CountDownLatch(2);
        var success = new AtomicInteger(0);
        var conflict = new AtomicInteger(0);
        var errors = new AtomicInteger(0);

        // Use direct JDBC to simulate two concurrent activation attempts
        Runnable task = () -> {
            try { barrier.await(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
            try {
                int rows = jdbc.update(
                    "UPDATE users SET password_hash=?, account_status='NORMAL', activation_issued_at=NULL, activation_expires_at=NULL, updated_at=now() WHERE id=? AND account_status='PENDING_ACTIVATION'",
                    "$2a$10$newHashForWinnerTest", userId);
                if (rows == 1) success.incrementAndGet();
                else conflict.incrementAndGet();
            } catch (Exception e) { errors.incrementAndGet(); }
            finally { latch.countDown(); }
        };

        var t1 = new Thread(task); var t2 = new Thread(task);
        t1.start(); t2.start();
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        // Exactly one winner, one conflict
        assertThat(success.get()).as("success count").isEqualTo(1);
        assertThat(conflict.get()).as("conflict count").isEqualTo(1);
        assertThat(errors.get()).as("unexpected errors").isEqualTo(0);

        // Final state is NORMAL
        String status = jdbc.queryForObject("SELECT account_status FROM users WHERE id=?", String.class, userId);
        assertThat(status).isEqualTo("NORMAL");

        // Verify account_status constraint in SQL prevented double update
        assertThat(success.get()).isEqualTo(1);
    }
}
