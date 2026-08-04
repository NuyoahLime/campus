package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.PasswordPolicy;
import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class AccountProvisioningService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SchoolMembershipQueryPort membershipPort;
    private final LoginNameNormalizer normalizer;
    private final TemporaryPasswordGenerator passwordGenerator;

    public AccountProvisioningService(JdbcTemplate jdbc, PasswordEncoder encoder,
            SchoolMembershipQueryPort membershipPort, LoginNameNormalizer normalizer,
            TemporaryPasswordGenerator passwordGenerator) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.membershipPort = membershipPort;
        this.normalizer = normalizer;
        this.passwordGenerator = passwordGenerator;
    }

    /**
     * Public-facing result: temporaryPassword is included ONLY on creation.
     * Must never appear in list/summary responses.
     */
    public record ProvisioningResult(UUID userId, String username, String role, UUID schoolId, String schoolName, String accountStatus, String temporaryPassword) {}
    public record AccountItem(UUID userId, String username, String role, String schoolName, String accountStatus, java.time.Instant createdAt) {}

    public ProvisioningResult createSchoolAdmin(UUID actorId, UUID schoolId, String username) {
        String trimmed = normalizer.normalize(username);
        // Check duplicate
        int count = jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ?", Integer.class, trimmed);
        if (count > 0) throw new DuplicateUsernameException(trimmed);

        // Validate school exists
        var schoolRow = jdbc.queryForList("SELECT name, school_status FROM schools WHERE id = ?", schoolId);
        if (schoolRow.isEmpty()) throw new IllegalArgumentException("SCHOOL_NOT_FOUND");
        if ("DISABLED".equals(schoolRow.getFirst().get("school_status"))) throw new IllegalStateException("SCHOOL_NOT_AVAILABLE");

        String tempPassword = passwordGenerator.generate();

        UUID userId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role,activation_issued_at,activation_expires_at) VALUES (?,?,?,?,?,now(),now() + INTERVAL '72 hours')", userId, trimmed, encoder.encode(tempPassword), "PENDING_ACTIVATION", null);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateUsernameException(trimmed);
        }
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), userId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        auditProvisioning(actorId, userId, schoolId, "SCHOOL_ADMIN", "CREATE_SCHOOL_ADMIN");
        String schoolName = (String) schoolRow.getFirst().get("name");
        return new ProvisioningResult(userId, trimmed, "SCHOOL_ADMIN", schoolId, schoolName, "PENDING_ACTIVATION", tempPassword);
    }

    public ProvisioningResult createTeacherOrStudent(UUID actorId, UUID actorSchoolId, String username, String role) {
        if (!"TEACHER".equals(role) && !"STUDENT".equals(role)) throw new IllegalArgumentException("Only TEACHER or STUDENT allowed");

        String trimmed = normalizer.normalize(username);
        int count = jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ?", Integer.class, trimmed);
        if (count > 0) throw new DuplicateUsernameException(trimmed);

        // School must be NORMAL
        String schoolStatus = jdbc.queryForObject("SELECT school_status FROM schools WHERE id = ?", String.class, actorSchoolId);
        if (!"NORMAL".equals(schoolStatus)) throw new IllegalStateException("SCHOOL_NOT_ACTIVE");

        String tempPassword = passwordGenerator.generate();

        UUID userId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role,activation_issued_at,activation_expires_at) VALUES (?,?,?,?,?,now(),now() + INTERVAL '72 hours')", userId, trimmed, encoder.encode(tempPassword), "PENDING_ACTIVATION", null);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateUsernameException(trimmed);
        }
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)", UUID.randomUUID(), userId, actorSchoolId, role, "ACTIVE");
        auditProvisioning(actorId, userId, actorSchoolId, role, "CREATE_" + role);
        String schoolName = jdbc.queryForObject("SELECT name FROM schools WHERE id = ?", String.class, actorSchoolId);
        return new ProvisioningResult(userId, trimmed, role, actorSchoolId, schoolName, "PENDING_ACTIVATION", tempPassword);
    }

    @Transactional(readOnly = true)
    public List<AccountItem> listSchoolAccounts(UUID schoolId, String role, String status, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder("WHERE sm.school_id = ?");
        List<Object> params = new ArrayList<>(); params.add(schoolId);
        if (role != null && !role.isBlank()) { where.append(" AND sm.role_in_school = ?"); params.add(role); }
        if (status != null && !status.isBlank()) { where.append(" AND u.account_status = ?"); params.add(status); }
        if (keyword != null && !keyword.isBlank()) { where.append(" AND u.username ILIKE ?"); params.add("%" + keyword.trim() + "%"); }
        String sql = "SELECT u.id,u.username,sm.role_in_school,s.name AS school_name,u.account_status,u.created_at FROM users u JOIN school_memberships sm ON u.id=sm.user_id JOIN schools s ON sm.school_id=s.id " + where + " ORDER BY u.created_at DESC,u.id DESC LIMIT ? OFFSET ?";
        params.add(size); params.add(page * size);
        return jdbc.queryForList(sql, params.toArray()).stream().map(r -> new AccountItem((UUID)r.get("id"),(String)r.get("username"),(String)r.get("role_in_school"),(String)r.get("school_name"),(String)r.get("account_status"),((java.sql.Timestamp)r.get("created_at")).toInstant())).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountItem> listSchoolAdmins(UUID schoolId, int page, int size) {
        return listSchoolAccounts(schoolId, "SCHOOL_ADMIN", null, null, page, size);
    }

    private void auditProvisioning(UUID actorId, UUID targetId, UUID schoolId, String role, String action) {
        jdbc.update("INSERT INTO account_provisioning_audit_logs(id,actor_id,target_user_id,school_id,role,action,occurred_at) VALUES (?,?,?,?,?,?,now())", UUID.randomUUID(), actorId, targetId, schoolId, role, action);
    }

    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String username) { super("Username already exists: " + username); }
    }
}
