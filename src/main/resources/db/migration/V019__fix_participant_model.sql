-- V019: fix participant model — rebuild activity_project_participants
-- Removes incorrect activity_application_id FK and establishes proper participant relationships.
-- activity_participants already exists from V005 — only activity_project_participants is rebuilt.

-- Step 1: Guard against legacy data in old activity_project_participants
DO $$
DECLARE
    legacy_count integer;
BEGIN
    SELECT count(*) INTO legacy_count FROM activity_project_participants;
    IF legacy_count > 0 THEN
        RAISE EXCEPTION 'V019 migration blocked: % legacy row(s) exist in activity_project_participants. '
            'The old table references activity_applications which is semantically incorrect. '
            'Manual data migration is required before applying V019. '
            'See docs for migration guidance.', legacy_count;
    END IF;
END $$;

-- Step 2: Drop old activity_project_participants table
DROP TABLE IF EXISTS activity_project_participants CASCADE;

-- Step 3: Recreate activity_project_participants with correct FK to activity_participants
CREATE TABLE activity_project_participants (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    activity_project_id     uuid          NOT NULL,
    activity_participant_id uuid          NOT NULL,
    assigned_by             uuid          NOT NULL,
    assigned_at             timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_project_participant UNIQUE (activity_project_id, activity_participant_id),
    CONSTRAINT fk_app_activity_project FOREIGN KEY (activity_project_id)
        REFERENCES activity_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_participant FOREIGN KEY (activity_participant_id)
        REFERENCES activity_participants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_app_activity_project
    ON activity_project_participants(activity_project_id);
