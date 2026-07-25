package com.campusguinness.interfaces.web.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountActivationController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AccountActivationController(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activate(@Valid @RequestBody ActivateRequest req) {
        // Validate passwords match
        if (!req.newPassword().equals(req.confirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("code","PASSWORD_MISMATCH","message","两次输入的密码不一致"));
        }
        // Validate new != temp
        if (req.newPassword().equals(req.temporaryPassword())) {
            return ResponseEntity.badRequest().body(Map.of("code","PASSWORD_SAME_AS_TEMP","message","新密码不能与临时密码相同"));
        }

        // Verify credentials
        var rows = jdbc.queryForList("SELECT id, username, password_hash, account_status FROM users WHERE username = ? AND account_status = 'PENDING_ACTIVATION'", req.username());
        if (rows.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("code","ACTIVATION_CREDENTIALS_INVALID","message","用户名或临时密码错误"));
        }
        var row = rows.getFirst();
        String hash = (String) row.get("password_hash");
        if (!encoder.matches(req.temporaryPassword(), hash)) {
            return ResponseEntity.status(401).body(Map.of("code","ACTIVATION_CREDENTIALS_INVALID","message","用户名或临时密码错误"));
        }

        // Activate: replace password hash + set NORMAL
        jdbc.update("UPDATE users SET password_hash = ?, account_status = 'NORMAL' WHERE id = ?", encoder.encode(req.newPassword()), row.get("id"));
        return ResponseEntity.ok(Map.of("message","账号激活成功，请返回登录"));
    }

    public record ActivateRequest(@NotBlank String username, @NotBlank String temporaryPassword, @NotBlank @Size(min=8) String newPassword, @NotBlank String confirmPassword) {}
}
