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
        jdbc.update("INSERT INTO schools(id,name,unified_code_type,unified_code,internal_code,school_type,region,address,contact_name,contact_phone,contact_email,school_status) VALUES (?,?,'USCC','SC-"+u+"','INT-"+u+"','PRIMARY','Test','Addr','Name','12345','a@b.com','NORMAL')", schoolId);
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)", actorId, "prov-"+u, "$2a$10$hAnonDummyHashForTest", "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM account_provisioning_audit_logs WHERE actor_id=?", actorId);
        jdbc.update("DELETE FROM school_memberships WHERE school_id=?", schoolId);
        jdbc.update("DELETE FROM users WHERE username LIKE 'prov-%'");
        jdbc.update("DELETE FROM schools WHERE id=?", schoolId);
    }

    @Test void createSchoolAdminSucceeds() {
        var r = svc.createSchoolAdmin(actorId, schoolId, "prov-" + UUID.randomUUID().toString().substring(0,8), "TempPass1!");
        assertThat(r.role()).isEqualTo("SCHOOL_ADMIN");
        assertThat(r.accountStatus()).isEqualTo("PENDING_ACTIVATION");
        // User and membership created in same transaction
        Integer userCount = jdbc.queryForObject("SELECT count(*) FROM users WHERE username=?", Integer.class, r.username());
        assertThat(userCount).isEqualTo(1);
        Integer memCount = jdbc.queryForObject("SELECT count(*) FROM school_memberships WHERE user_id=?", Integer.class, r.userId());
        assertThat(memCount).isEqualTo(1);
        // Audit written
        Integer auditCount = jdbc.queryForObject("SELECT count(*) FROM account_provisioning_audit_logs WHERE target_user_id=?", Integer.class, r.userId());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test void duplicateUsernameReturns409() {
        String un = "prov-" + UUID.randomUUID().toString().substring(0,8);
        svc.createSchoolAdmin(actorId, schoolId, un, "TempPass1!");
        assertThatThrownBy(() -> svc.createSchoolAdmin(actorId, schoolId, un, "TempPass2!"))
                .isInstanceOf(AccountProvisioningService.DuplicateUsernameException.class);
    }
}
