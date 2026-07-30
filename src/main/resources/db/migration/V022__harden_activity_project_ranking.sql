-- V022: harden L1 activity-project ranking publication.
-- V019 is already occupied by the participant-model correction in this baseline.

ALTER TABLE ranking_definitions
    ADD COLUMN activity_project_id uuid NULL;

ALTER TABLE ranking_definitions
    ADD CONSTRAINT fk_ranking_def_activity_project
    FOREIGN KEY (activity_project_id)
    REFERENCES activity_projects(id)
    ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_ranking_definition_activity_project
    ON ranking_definitions(activity_project_id)
    WHERE activity_project_id IS NOT NULL;

ALTER TABLE ranking_versions
    ADD COLUMN published_by uuid NULL;

ALTER TABLE ranking_versions
    ADD CONSTRAINT fk_ranking_version_publisher
    FOREIGN KEY (published_by)
    REFERENCES users(id)
    ON DELETE RESTRICT;

ALTER TABLE ranking_versions
    ADD CONSTRAINT fk_ranking_version_withdrawer
    FOREIGN KEY (withdrawn_by)
    REFERENCES users(id)
    ON DELETE RESTRICT;

CREATE INDEX idx_ranking_definition_school_project
    ON ranking_definitions(school_id, activity_project_id);

CREATE INDEX idx_ranking_version_definition_number
    ON ranking_versions(definition_id, version_number DESC);

CREATE INDEX idx_ranking_version_definition_status
    ON ranking_versions(definition_id, version_status);

CREATE INDEX idx_ranking_version_published_by
    ON ranking_versions(published_by);

CREATE INDEX idx_ranking_version_withdrawn_by
    ON ranking_versions(withdrawn_by);

CREATE UNIQUE INDEX uq_ranking_entry_single_score_source
    ON ranking_entry_score_sources(entry_id);

CREATE INDEX idx_ranking_entry_version_position
    ON ranking_entries(version_id, rank_position, student_id);
