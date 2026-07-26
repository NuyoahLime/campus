package com.campusguinness.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationAuditService {
    private static final Logger log = LoggerFactory.getLogger(ActivationAuditService.class);
    private final JdbcTemplate jdbc;

    public ActivationAuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String username, String result, String failureCode, String ip, String ua) {
        try {
            jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,failure_code,ip_address,user_agent,occurred_at) VALUES (?,?,?,?,?,?,?,now())",
                    UUID.randomUUID(), userId, username.toLowerCase().trim(), result, failureCode, ip, ua);
        } catch (Exception e) {
            log.error("Failed to write activation audit for user {}: {}", username, e.getMessage());
        }
    }
}
