-- V008: appeal domain (score_appeals, appeal_records)
-- FK count: 7

CREATE TABLE score_appeals (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id           uuid          NOT NULL,
    score_attempt_id    uuid          NOT NULL,
    student_id          uuid          NOT NULL,
    appeal_type         varchar(32)   NOT NULL,
    appeal_reason       text          NOT NULL,
    evidence_file_keys  jsonb         NULL,
    appeal_status       varchar(32)   NOT NULL DEFAULT 'SUBMITTED',
    handler_id          uuid          NULL,
    escalated_to        uuid          NULL,
    resolution          text          NULL,
    resolved_at         timestamptz   NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    version             integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_appeal_type CHECK (
        appeal_type IN ('SCORE','RANKING')
    ),
    CONSTRAINT chk_appeal_status CHECK (
        appeal_status IN ('SUBMITTED','PROCESSING','REJECTED','ACCEPTED_PENDING_CORRECTION',
            'SCORE_CORRECTING','RANK_CHECKING','RANK_FIXING','ESCALATED','PLATFORM_PROCESSING',
            'RETURNED_TO_SCHOOL','PLATFORM_DECIDED','RESOLVED','WITHDRAWN')
    ),
    CONSTRAINT fk_appeal_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_appeal_score_attempt FOREIGN KEY (score_attempt_id)
        REFERENCES score_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_appeal_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_appeal_handler FOREIGN KEY (handler_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_appeal_escalated_to FOREIGN KEY (escalated_to)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE appeal_records (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    appeal_id       uuid          NOT NULL,
    from_status     varchar(32)   NULL,
    to_status       varchar(32)   NOT NULL,
    operator_id     uuid          NOT NULL,
    comment         text          NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_appeal_record_appeal FOREIGN KEY (appeal_id)
        REFERENCES score_appeals(id) ON DELETE RESTRICT,
    CONSTRAINT fk_appeal_record_operator FOREIGN KEY (operator_id)
        REFERENCES users(id) ON DELETE RESTRICT
);
