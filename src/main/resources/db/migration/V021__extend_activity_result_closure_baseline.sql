-- V021: ActivityResult closure persistence baseline

ALTER TABLE activity_results
    ADD COLUMN current_candidate_version_id uuid NULL,
    ADD COLUMN public_visibility_blocked boolean NOT NULL DEFAULT false;

ALTER TABLE activity_results
    ADD CONSTRAINT fk_result_current_candidate_version
    FOREIGN KEY (current_candidate_version_id, id)
    REFERENCES result_versions(id, result_id)
    ON DELETE RESTRICT;
