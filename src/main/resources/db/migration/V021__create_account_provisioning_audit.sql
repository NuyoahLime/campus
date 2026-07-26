-- V021: account provisioning audit log

CREATE TABLE account_provisioning_audit_logs (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    actor_id        uuid          NOT NULL,
    target_user_id  uuid          NOT NULL,
    school_id       uuid          NOT NULL,
    role            varchar(32)   NOT NULL,
    action          varchar(64)   NOT NULL,
    occurred_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_prov_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_prov_audit_target FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_prov_audit_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE RESTRICT
);

CREATE INDEX idx_prov_audit_actor ON account_provisioning_audit_logs(actor_id);
CREATE INDEX idx_prov_audit_target ON account_provisioning_audit_logs(target_user_id);
CREATE INDEX idx_prov_audit_school ON account_provisioning_audit_logs(school_id);
CREATE INDEX idx_prov_audit_occurred ON account_provisioning_audit_logs(occurred_at DESC);
