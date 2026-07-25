package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.UserAccountProvisioningPort;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;

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

    public AccountProvisioningService(JdbcTemplate jdbc, PasswordEncoder encoder,
            SchoolMembershipQueryPort membershipPort) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.membershipPort = membershipPort;
    }

    public record ProvisioningResult(UUID userId, String username, String role, UUID schoolId, String schoolName, String accountStatus) {}

    public record AccountItem(UUID userId, String username, String role, String schoolName, String accountStatus, java.time.Instant createdAt) {}

    public ProvisioningResult createSchoolAdmin(UUID actorId, UUID schoolId, String username, String tempPassword) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, encoder.encode(tempPassword), "PENDING_ACTIVATION", null);
        UUID membershipId = UUID.randomUUID();
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                membershipId, userId, schoolId, "SCHOOL_ADMIN", "ACTIVE");
        String schoolName = jdbc.queryForObject("SELECT name FROM schools WHERE id = ?", String.class, schoolId);
        return new ProvisioningResult(userId, username, "SCHOOL_ADMIN", schoolId, schoolName, "PENDING_ACTIVATION");
    }

    public ProvisioningResult createTeacherOrStudent(UUID actorId, UUID actorSchoolId, String username, String tempPassword, String role) {
        if (!"TEACHER".equals(role) && !"STUDENT".equals(role))
            throw new IllegalArgumentException("Only TEACHER or STUDENT allowed");

        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,username,password_hash,account_status,platform_role) VALUES (?,?,?,?,?)",
                userId, username, encoder.encode(tempPassword), "PENDING_ACTIVATION", null);
        UUID membershipId = UUID.randomUUID();
        jdbc.update("INSERT INTO school_memberships(id,user_id,school_id,role_in_school,status,started_at,created_at,version) VALUES (?,?,?,?,?,now(),now(),1)",
                membershipId, userId, actorSchoolId, role, "ACTIVE");
        String schoolName = jdbc.queryForObject("SELECT name FROM schools WHERE id = ?", String.class, actorSchoolId);
        return new ProvisioningResult(userId, username, role, actorSchoolId, schoolName, "PENDING_ACTIVATION");
    }

    @Transactional(readOnly = true)
    public List<AccountItem> listSchoolAccounts(UUID schoolId, String role, String status, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder("WHERE sm.school_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(schoolId);
        if (role != null && !role.isBlank()) { where.append(" AND sm.role_in_school = ?"); params.add(role); }
        if (status != null && !status.isBlank()) { where.append(" AND u.account_status = ?"); params.add(status); }
        if (keyword != null && !keyword.isBlank()) { where.append(" AND u.username ILIKE ?"); params.add("%" + keyword + "%"); }

        String sql = "SELECT u.id, u.username, sm.role_in_school, s.name AS school_name, u.account_status, u.created_at FROM users u JOIN school_memberships sm ON u.id = sm.user_id JOIN schools s ON sm.school_id = s.id " + where + " ORDER BY u.created_at DESC LIMIT ? OFFSET ?";
        params.add(size); params.add(page * size);
        return jdbc.queryForList(sql, params.toArray()).stream().map(row -> new AccountItem(
                (UUID) row.get("id"), (String) row.get("username"), (String) row.get("role_in_school"),
                (String) row.get("school_name"), (String) row.get("account_status"),
                ((java.sql.Timestamp) row.get("created_at")).toInstant())).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountItem> listSchoolAdmins(UUID schoolId) {
        return listSchoolAccounts(schoolId, "SCHOOL_ADMIN", null, null, 0, 100);
    }
}
