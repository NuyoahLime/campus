-- V022: candidate-bound, append-only ActivityResult public review history

CREATE TABLE result_review_records (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    result_id           uuid        NOT NULL,
    result_version_id   uuid        NOT NULL,
    action              varchar(32) NOT NULL,
    submitted_by        uuid        NULL,
    submitted_at        timestamptz NULL,
    reviewer_id         uuid        NULL,
    reviewed_at         timestamptz NULL,
    reason              text        NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_result_review_action CHECK (
        action IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'TAKEDOWN')
    ),
    CONSTRAINT chk_result_review_action_fields CHECK (
        (action = 'SUBMITTED'
            AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL
            AND reviewer_id IS NULL AND reviewed_at IS NULL AND reason IS NULL)
        OR
        (action = 'APPROVED'
            AND submitted_by IS NULL AND submitted_at IS NULL
            AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND reason IS NULL)
        OR
        (action IN ('REJECTED', 'TAKEDOWN')
            AND submitted_by IS NULL AND submitted_at IS NULL
            AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND reason IS NOT NULL AND length(btrim(reason)) BETWEEN 1 AND 2000)
    ),
    CONSTRAINT fk_result_review_result FOREIGN KEY (result_id)
        REFERENCES activity_results(id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_review_version_same_result
        FOREIGN KEY (result_version_id, result_id)
        REFERENCES result_versions(id, result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_review_submitter FOREIGN KEY (submitted_by)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_review_reviewer FOREIGN KEY (reviewer_id)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_result_review_result_history
    ON result_review_records(result_id, created_at, id);

CREATE INDEX idx_result_review_version_history
    ON result_review_records(result_version_id, created_at, id);
