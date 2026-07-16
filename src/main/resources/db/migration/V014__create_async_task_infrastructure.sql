-- V014: async task infrastructure (task_records)
-- FK count: 0

CREATE TABLE task_records (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    task_type           varchar(64)   NOT NULL,
    reference_type      varchar(32)   NULL,
    reference_id        uuid          NULL,
    task_status         varchar(32)   NOT NULL DEFAULT 'PENDING',
    payload             jsonb         NULL,
    retry_count         integer       NOT NULL DEFAULT 0,
    max_retries         integer       NOT NULL DEFAULT 3,
    next_retry_at       timestamptz   NULL,
    last_error          text          NULL,
    idempotency_key     varchar(128)  NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    version             integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_task_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_task_type CHECK (
        task_type IN ('RANKING_RECALC','NOTIFY','SCHOOL_STATE_SYNC','MEDIA_CLEANUP')
    ),
    CONSTRAINT chk_task_status CHECK (
        task_status IN ('PENDING','PROCESSING','COMPLETED','FAILED','DEAD')
    )
);

CREATE INDEX idx_task_pending ON task_records(task_status, next_retry_at)
    WHERE task_status = 'PENDING';
