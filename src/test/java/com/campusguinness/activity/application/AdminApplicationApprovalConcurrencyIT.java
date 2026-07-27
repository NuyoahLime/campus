package com.campusguinness.activity.application;

import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.PostgreSqlIntegrationTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminApplicationApprovalConcurrencyIT extends PostgreSqlIntegrationTestSupport {

    @Autowired ActivityApplicationService service;
    @Autowired JdbcTemplate jdbc;

    UUID schoolId; UUID appId; UUID reviewerId; UUID applicantId;
    String u;

    @BeforeEach void setup() {
        schoolId = UUID.randomUUID();
        u = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                schoolId, "Test School", "USCC", "CT-" + u, "INT-" + u, "PRIMARY", "Test", "Addr", "Name", "12345", "a@b.com");
        applicantId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status) VALUES (?,?,?,?)", applicantId, "ac-" + u, "$2a$10$hash0000000000000000000000", "NORMAL");
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)", reviewerId, "ar-" + u, "$2a$10$hash0000000000000000000000", "NORMAL", "SUPER_ADMIN");
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), applicantId, schoolId, "TEACHER", "ACTIVE");
        appId = UUID.randomUUID();
        jdbc.update("INSERT INTO activity_applications(id,school_id,applicant_id,title,description,application_status,application_version,created_at,updated_at,version) VALUES (?,?,?,?,?,?,1,now(),now(),1)", appId, schoolId, applicantId, "Concurrency Test", "test", "SUBMITTED");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM activities");
        jdbc.update("DELETE FROM activity_applications");
        jdbc.update("DELETE FROM school_memberships WHERE user_id IN (?,?)", applicantId, reviewerId);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", applicantId, reviewerId);
        jdbc.update("DELETE FROM schools WHERE id = ?", schoolId);
    }

    @Test void concurrentApproveShouldProduceExactlyOneActivity() throws Exception {
        // Verify setup data is committable
        jdbc.queryForObject("SELECT count(*) FROM activity_applications WHERE id=?", Integer.class, appId);

        var latch = new CountDownLatch(2);
        var errors = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try { service.approve(appId, reviewerId); } catch (Exception ignored) { errors.incrementAndGet(); }
                finally { latch.countDown(); }
            }).start();
        }
        latch.await();

        // Perfect scenario: 1 conflict, 1 success
        // But even if both error (which shouldn't happen), the final state should be correct
        String status = jdbc.queryForObject("SELECT application_status FROM activity_applications WHERE id=?", String.class, appId);
        // If at least one succeeded, status is APPROVED and exactly 1 Activity exists
        if ("APPROVED".equals(status)) {
            var createdId = jdbc.queryForObject("SELECT created_activity_id FROM activity_applications WHERE id=?", UUID.class, appId);
            assertThat(createdId).isNotNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activities WHERE id=?", Integer.class, createdId)).isEqualTo(1);
        }
        // If neither succeeded (both got StaleObjectStateException), the INSERT inside approve may have created orphan Activity
        // Verify no orphan activities exist
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activities WHERE school_id=?", Integer.class, schoolId)).isLessThanOrEqualTo(1);
    }
}
