-- V011: feedback domain (feedbacks)
-- FK count: 3

CREATE TABLE feedbacks (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id       uuid          NULL,
    submitter_id    uuid          NULL,
    feedback_type   varchar(32)   NOT NULL,
    content         text          NOT NULL,
    feedback_status varchar(32)   NOT NULL DEFAULT 'SUBMITTED',
    handler_id      uuid          NULL,
    handler_level   varchar(32)   NULL,
    reply           text          NULL,
    close_reason    text          NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_feedback_type CHECK (
        feedback_type IN ('GENERAL','SCORE_PROBLEM','RANKING_PROBLEM')
    ),
    CONSTRAINT chk_feedback_status CHECK (
        feedback_status IN ('SUBMITTED','PROCESSING','RESOLVED','ESCALATED','CLOSED')
    ),
    CONSTRAINT fk_feedback_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_submitter FOREIGN KEY (submitter_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_handler FOREIGN KEY (handler_id)
        REFERENCES users(id) ON DELETE RESTRICT
);
