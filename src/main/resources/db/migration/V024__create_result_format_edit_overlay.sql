CREATE TABLE result_format_edit_records (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    result_id uuid NOT NULL,
    result_version_id uuid NOT NULL,
    revision integer NOT NULL CHECK (revision >= 1),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    reason text NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 2000),
    edited_by uuid NOT NULL,
    edited_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_result_format_record_revision UNIQUE (result_version_id, revision),
    CONSTRAINT uq_result_format_record_identity UNIQUE (id, result_id, result_version_id),
    CONSTRAINT fk_result_format_record_result FOREIGN KEY (result_id)
        REFERENCES activity_results(id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_format_record_version FOREIGN KEY (result_version_id, result_id)
        REFERENCES result_versions(id, result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_format_record_editor FOREIGN KEY (edited_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE result_format_heads (
    result_version_id uuid PRIMARY KEY,
    result_id uuid NOT NULL,
    current_format_edit_record_id uuid NOT NULL,
    version integer NOT NULL CHECK (version >= 1),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_result_format_head_identity UNIQUE (result_version_id, result_id),
    CONSTRAINT fk_result_format_head_version FOREIGN KEY (result_version_id, result_id)
        REFERENCES result_versions(id, result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_format_head_record FOREIGN KEY (
        current_format_edit_record_id, result_id, result_version_id)
        REFERENCES result_format_edit_records(id, result_id, result_version_id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_result_format_records_history
    ON result_format_edit_records(result_version_id, revision);
