package com.campusguinness.infrastructure.security;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class AccountProvisioningServiceIT extends PostgreSqlIntegrationTestSupport {
    @Autowired AccountProvisioningService svc;
    @Autowired JdbcTemplate jdbc;
    UUID schoolId; UUID actorId; String u;

    @BeforeEach void setup() {
        schoolId = UUID.randomUUID(); actorId = UUID.randomUUID();
        u = UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,'USCC','SC-"+u+"','INT-"+u+"','PRIMARY','Test','Addr','Name','12345','a@b.com','NORMAL')", schoolId, "Provisioning Test School " + u);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)", actorId, "prov-"+u, "$2a$10$hAnonDummyHashForTest", "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM account_provisioning_audit_logs WHERE actor_id=?", actorId);
        jdbc.update("DELETE FROM school_memberships WHERE school_id=?", schoolId);
        jdbc.update("DELETE FROM users WHERE username LIKE 'prov-%'");
        jdbc.update("DELETE FROM schools WHERE id=?", schoolId);
    }

    @Test void createSchoolAdminSucceeds() {
        var r = svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8));
        assertThat(r.role()).isEqualTo("SCHOOL_ADMIN");
        assertThat(r.accountStatus()).isEqualTo("PENDING_ACTIVATION");
        assertThat(r.temporaryPassword()).isNotBlank();
        // User and membership created in same transaction
        Integer userCount = jdbc.queryForObject("SELECT count(*) FROM users WHERE username=?", Integer.class, r.username());
        assertThat(userCount).isEqualTo(1);
        Integer memCount = jdbc.queryForObject("SELECT count(*) FROM school_memberships WHERE user_id=?", Integer.class, r.userId());
        assertThat(memCount).isEqualTo(1);
        // Audit written
        Integer auditCount = jdbc.queryForObject("SELECT count(*) FROM account_provisioning_audit_logs WHERE target_user_id=?", Integer.class, r.userId());
        assertThat(auditCount).isEqualTo(1);
        // Activation timestamps set
        var row = jdbc.queryForMap("SELECT activation_issued_at, activation_expires_at FROM users WHERE id=?", r.userId());
        assertThat(row.get("activation_issued_at")).isNotNull();
        assertThat(row.get("activation_expires_at")).isNotNull();
    }

    @Test void generatedTemporaryPasswordMeetsMinimumLength() {
        var r = svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8));
        assertThat(r.temporaryPassword()).isNotBlank();
        assertThat(r.temporaryPassword().length()).isGreaterThanOrEqualTo(16);
    }

    @Test void generatedTemporaryPasswordsAreNotConstant() {
        var r1 = svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8));
        var r2 = svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8));
        assertThat(r1.temporaryPassword()).isNotEqualTo(r2.temporaryPassword());
    }

    @Test void temporaryPasswordIsStoredOnlyAsHash() {
        var r = svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8));
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, r.userId());
        assertThat(storedHash).isNotNull();
        // BCrypt hashes always start with $2a$, $2b$, or $2y$
        assertThat(storedHash).startsWith("$2");
        assertThat(storedHash).isNotEqualTo(r.temporaryPassword());
    }

    @Test void duplicateUsernameReturns409() {
        String un = "prov-" + UUID.randomUUID().toString().substring(0,8);
        svc.createSchoolAdmin(actorId, schoolId, un);
        assertThatThrownBy(() -> svc.createSchoolAdmin(actorId, schoolId, un))
                .isInstanceOf(AccountProvisioningService.DuplicateUsernameException.class);
    }

    @Test void createTeacherOrStudentSucceeds() {
        var r = svc.createTeacherOrStudent(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8), "STUDENT");
        assertThat(r.role()).isEqualTo("STUDENT");
        assertThat(r.temporaryPassword()).isNotBlank();
        assertThat(r.accountStatus()).isEqualTo("PENDING_ACTIVATION");
    }

    @Test void listResponseDoesNotContainTemporaryPassword() {
        svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8));
        var list = svc.listSchoolAdmins(schoolId, 0, 100);
        assertThat(list).isNotEmpty();
        // AccountItem record has no temporaryPassword field at all
        for (var item : list) {
            assertThat(item.toString()).doesNotContain("temporaryPassword");
        }
    }

    @Test void createAccountSetsActivationExpiry() {
        var r = svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8));
        var row = jdbc.queryForMap("SELECT activation_issued_at, activation_expires_at FROM users WHERE id=?", r.userId());
        java.sql.Timestamp issued = (java.sql.Timestamp) row.get("activation_issued_at");
        java.sql.Timestamp expires = (java.sql.Timestamp) row.get("activation_expires_at");
        // Expiry must be after issued
        assertThat(expires).isAfter(issued);
        // Approximately 72 hours (allow ±5 minute tolerance)
        long diffMs = expires.getTime() - issued.getTime();
        long expectedMs = 72 * 60 * 60 * 1000L;
        assertThat(Math.abs(diffMs - expectedMs)).isLessThan(5 * 60 * 1000L);
    }
}
