-- V020: activation audit log — immutable record of activation attempts

CREATE TABLE activation_audit_logs (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id             uuid          NULL,
    username_normalized varchar(100)  NOT NULL,
    result              varchar(32)   NOT NULL,
    failure_code        varchar(64)   NULL,
    ip_address          varchar(45)   NULL,
    user_agent          text          NULL,
    occurred_at         timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_result CHECK (
        result IN ('SUCCESS','FAILURE','DUPLICATE','RATE_LIMITED')
    )
);

CREATE INDEX idx_audit_username ON activation_audit_logs(username_normalized);
CREATE INDEX idx_audit_occurred ON activation_audit_logs(occurred_at DESC);
