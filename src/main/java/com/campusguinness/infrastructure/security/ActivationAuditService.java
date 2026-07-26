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

    /** SUCCESS: shares the same transaction as password/status update — commit together or rollback together */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSuccess(UUID userId, String username, String ip, String ua) {
        jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,failure_code,ip_address,user_agent,occurred_at) VALUES (?,?,?,'SUCCESS',NULL,?,?,now())",
                UUID.randomUUID(), userId, username, ip, ua);
    }

    /** FAILURE: independent transaction — must be preserved even if later activation fails */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, String username, String failureCode, String ip, String ua) {
        jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,failure_code,ip_address,user_agent,occurred_at) VALUES (?,?,?,'FAILURE',?,?,?,now())",
                UUID.randomUUID(), userId, username, failureCode, ip, ua);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDuplicate(UUID userId, String username, String failureCode, String ip, String ua) {
        jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,failure_code,ip_address,user_agent,occurred_at) VALUES (?,?,?,'DUPLICATE',?,?,?,now())",
                UUID.randomUUID(), userId, username, failureCode, ip, ua);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRateLimited(String username, String ip, String ua) {
        jdbc.update("INSERT INTO activation_audit_logs(id,user_id,username_normalized,result,failure_code,ip_address,user_agent,occurred_at) VALUES (?,NULL,?,'RATE_LIMITED','RATE_LIMITED',?,?,now())",
                UUID.randomUUID(), username, ip, ua);
    }
}
