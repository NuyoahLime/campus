-- V023: immutable achievement snapshots, idempotent issuance, and query support

ALTER TABLE achievement_records
    ADD COLUMN school_name_snapshot varchar(200),
    ADD COLUMN activity_title_snapshot varchar(200),
    ADD COLUMN project_name_snapshot varchar(200),
    ADD COLUMN ranking_version_number_snapshot integer;

UPDATE achievement_records record
SET school_name_snapshot = school.name,
    activity_title_snapshot = activity.title,
    project_name_snapshot = project.name,
    ranking_version_number_snapshot = version.version_number
FROM activity_projects activity_project
JOIN activities activity
  ON activity.id = activity_project.activity_id
JOIN schools school
  ON school.id = activity.school_id
JOIN challenge_projects project
  ON project.id = activity_project.project_id
JOIN ranking_definitions definition
  ON definition.activity_project_id = activity_project.id
JOIN ranking_versions version
  ON version.definition_id = definition.id
WHERE activity_project.id = record.activity_project_id
  AND version.id = record.ranking_version_id;

ALTER TABLE achievement_records
    ALTER COLUMN school_name_snapshot SET NOT NULL,
    ALTER COLUMN activity_title_snapshot SET NOT NULL,
    ALTER COLUMN project_name_snapshot SET NOT NULL,
    ALTER COLUMN ranking_version_number_snapshot SET NOT NULL;

ALTER TABLE achievement_records
    ADD CONSTRAINT ck_achievement_verification_code
        CHECK (verification_code ~ '^[0-9a-f]{32}$'),
    ADD CONSTRAINT ck_achievement_status
        CHECK (status IN ('ACTIVE', 'REVOKED'));

CREATE UNIQUE INDEX uq_achievement_record_ranking_entry
    ON achievement_records(ranking_entry_id);

CREATE INDEX idx_ach_project_issued
    ON achievement_records(activity_project_id, issued_at DESC);

CREATE INDEX idx_ach_ranking_version
    ON achievement_records(ranking_version_id);
