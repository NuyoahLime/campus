-- V016: project participant assignment domain

CREATE TABLE activity_project_participants (
    id                            uuid PRIMARY KEY DEFAULT uuidv7(),
    activity_project_id           uuid          NOT NULL,
    activity_application_id       uuid          NOT NULL,
    assigned_by                   uuid          NOT NULL,
    assigned_at                   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_project_application UNIQUE (activity_project_id, activity_application_id),
    CONSTRAINT fk_app_activity_project FOREIGN KEY (activity_project_id)
        REFERENCES activity_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_application FOREIGN KEY (activity_application_id)
        REFERENCES activity_applications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_app_activity_project
    ON activity_project_participants(activity_project_id);
