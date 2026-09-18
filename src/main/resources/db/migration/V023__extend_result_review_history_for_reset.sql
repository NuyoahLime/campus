-- V023: add the SuperAdmin takedown-reset action to the append-only governance history

ALTER TABLE result_review_records
    DROP CONSTRAINT chk_result_review_action,
    DROP CONSTRAINT chk_result_review_action_fields;

ALTER TABLE result_review_records
    ADD CONSTRAINT chk_result_review_action CHECK (
        action IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'TAKEDOWN', 'RESET')
    ),
    ADD CONSTRAINT chk_result_review_action_fields CHECK (
        (action = 'SUBMITTED'
            AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL
            AND reviewer_id IS NULL AND reviewed_at IS NULL AND reason IS NULL)
        OR
        (action = 'APPROVED'
            AND submitted_by IS NULL AND submitted_at IS NULL
            AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND reason IS NULL)
        OR
        (action IN ('REJECTED', 'TAKEDOWN', 'RESET')
            AND submitted_by IS NULL AND submitted_at IS NULL
            AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND reason IS NOT NULL AND length(btrim(reason)) BETWEEN 1 AND 2000)
    );
