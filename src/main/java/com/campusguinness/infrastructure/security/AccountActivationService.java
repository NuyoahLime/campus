package com.campusguinness.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class AccountActivationService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AccountActivationService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public record ActivationResult(boolean success, String code, String message, UUID userId) {}

    public ActivationResult activate(String rawUsername, String tempPassword, String newPassword) {
        String username = rawUsername.trim();
        // Query by username — don't leak account state via different error messages
        var rows = jdbc.queryForList("SELECT id, password_hash, account_status FROM users WHERE username = ?", username);
        if (rows.isEmpty()) {
            return new ActivationResult(false, "ACTIVATION_CREDENTIALS_INVALID", "用户名或临时密码错误", null);
        }
        var row = rows.getFirst();
        String status = (String) row.get("account_status");
        UUID userId = (UUID) row.get("id");

        // Already-activated accounts return 409
        if ("NORMAL".equals(status) || "LOCKED".equals(status) || "DISABLED".equals(status)) {
            return new ActivationResult(false, "ACCOUNT_ALREADY_ACTIVATED", "账号已激活", userId);
        }

        // PENDING_ACTIVATION — verify temp password
        if (!encoder.matches(tempPassword, (String) row.get("password_hash"))) {
            return new ActivationResult(false, "ACTIVATION_CREDENTIALS_INVALID", "用户名或临时密码错误", null);
        }

        // Atomic condition update
        int updated = jdbc.update(
            "UPDATE users SET password_hash = ?, account_status = 'NORMAL', updated_at = now() WHERE id = ? AND account_status = 'PENDING_ACTIVATION'",
            encoder.encode(newPassword), userId);
        if (updated == 0) {
            return new ActivationResult(false, "ACCOUNT_ALREADY_ACTIVATED", "账号已激活", userId);
        }
        return new ActivationResult(true, "SUCCESS", "激活成功", userId);
    }
}
