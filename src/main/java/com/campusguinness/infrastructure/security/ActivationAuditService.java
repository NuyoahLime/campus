package com.campusguinness.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationAuditService {
    private final JdbcTemplate jdbc;

    public ActivationAuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String username, String result, String failureCode, String ip, String ua) {
        jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,failure_code,ip_address,user_agent,occurred_at) VALUES (?,?,?,?,?,?,?,now())",
                UUID.randomUUID(), userId, username.trim(), result, failureCode, ip, ua);
    }
}
