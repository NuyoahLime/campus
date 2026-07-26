package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.PasswordPolicy;
import com.campusguinness.identity.application.exception.InvalidPasswordException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AccountActivationService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ActivationAuditService audit;
    private final ActivationRateLimiter rateLimiter;
    private final LoginNameNormalizer normalizer;

    public AccountActivationService(JdbcTemplate jdbc, PasswordEncoder encoder,
            ActivationAuditService audit, ActivationRateLimiter rateLimiter,
            LoginNameNormalizer normalizer) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.normalizer = normalizer;
    }

    public record ActivationResult(boolean success, String code, String message, UUID userId) {}

    public ActivationResult activate(String rawUsername, String tempPassword, String newPassword,
            String clientIp, String userAgent) {
        String username = normalizer.normalize(rawUsername);

        // Rate limit check
        if (rateLimiter.isRateLimited(username, clientIp)) {
            audit.recordRateLimited(username, clientIp, userAgent);
            return new ActivationResult(false, "ACTIVATION_RATE_LIMITED", "尝试次数过多，请15分钟后重试", null);
        }

        // Validate new password via policy
        try {
            PasswordPolicy.validate(newPassword);
        } catch (InvalidPasswordException e) {
            return new ActivationResult(false, e.getMessage(), e.getMessage(), null);
        }

        // Query by username — all states
        var rows = jdbc.queryForList("SELECT id, password_hash, account_status FROM users WHERE username = ?", username);
        if (rows.isEmpty()) {
            rateLimiter.recordFailure(username, clientIp);
            audit.recordFailure(null, username, "ACTIVATION_CREDENTIALS_INVALID", clientIp, userAgent);
            return new ActivationResult(false, "ACTIVATION_CREDENTIALS_INVALID", "用户名或临时密码错误", null);
        }
        var row = rows.getFirst();
        String status = (String) row.get("account_status");
        UUID userId = (UUID) row.get("id");

        // Already-activated accounts return 409
        if ("NORMAL".equals(status) || "LOCKED".equals(status) || "DISABLED".equals(status)) {
            audit.recordDuplicate(userId, username, "ACCOUNT_ALREADY_ACTIVATED", clientIp, userAgent);
            return new ActivationResult(false, "ACCOUNT_ALREADY_ACTIVATED", "账号已激活", userId);
        }

        // Unknown status
        if (!"PENDING_ACTIVATION".equals(status)) {
            return new ActivationResult(false, "ACCOUNT_STATE_INVALID", "账号状态异常", userId);
        }

        // PENDING_ACTIVATION — verify temp password
        if (!encoder.matches(tempPassword, (String) row.get("password_hash"))) {
            rateLimiter.recordFailure(username, clientIp);
            audit.recordFailure(userId, username, "ACTIVATION_CREDENTIALS_INVALID", clientIp, userAgent);
            return new ActivationResult(false, "ACTIVATION_CREDENTIALS_INVALID", "用户名或临时密码错误", null);
        }

        // Atomic condition update
        int updated = jdbc.update(
            "UPDATE users SET password_hash = ?, account_status = 'NORMAL', updated_at = now() WHERE id = ? AND account_status = 'PENDING_ACTIVATION'",
            encoder.encode(newPassword), userId);
        if (updated == 0) {
            audit.recordDuplicate(userId, username, "ACCOUNT_ALREADY_ACTIVATED", clientIp, userAgent);
            return new ActivationResult(false, "ACCOUNT_ALREADY_ACTIVATED", "账号已激活", userId);
        }

        rateLimiter.clear(username, clientIp);
        audit.recordSuccess(userId, username, clientIp, userAgent);
        return new ActivationResult(true, "SUCCESS", "激活成功", userId);
    }
}
