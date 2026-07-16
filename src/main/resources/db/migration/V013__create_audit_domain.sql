-- V013: audit domain (audit_records)
-- FK count: 2

CREATE TABLE audit_records (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id       uuid          NULL,
    actor_id        uuid          NOT NULL,
    action          varchar(64)   NOT NULL,
    target_type     varchar(32)   NOT NULL,
    target_id       uuid          NOT NULL,
    detail          jsonb         NULL,
    ip_address      varchar(64)   NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id)
        REFERENCES users(id) ON DELETE RESTRICT
);
