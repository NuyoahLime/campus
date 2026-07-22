-- V018: achievement record domain

CREATE TABLE achievement_records (
    id                   uuid PRIMARY KEY DEFAULT uuidv7(),
    activity_project_id  uuid          NOT NULL,
    ranking_version_id   uuid          NOT NULL,
    ranking_entry_id     uuid          NOT NULL,
    student_id           uuid          NOT NULL,
    rank_snapshot        integer       NOT NULL,
    score_value_snapshot text          NOT NULL,
    score_storage_type   varchar(32)   NOT NULL,
    record_title         varchar(256)  NOT NULL DEFAULT 'Achievement Record',
    verification_code    varchar(64)   NOT NULL,
    status               varchar(32)   NOT NULL DEFAULT 'ACTIVE',
    issued_at            timestamptz   NOT NULL DEFAULT now(),
    issued_by            uuid          NOT NULL,
    revoked_at           timestamptz   NULL,
    revoked_by           uuid          NULL,
    revocation_reason    text          NULL,

    CONSTRAINT uq_achievement_version_entry UNIQUE (ranking_version_id, ranking_entry_id),
    CONSTRAINT uq_verification_code UNIQUE (verification_code),
    CONSTRAINT fk_ach_project FOREIGN KEY (activity_project_id) REFERENCES activity_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ach_ranking_version FOREIGN KEY (ranking_version_id) REFERENCES ranking_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ach_ranking_entry FOREIGN KEY (ranking_entry_id) REFERENCES ranking_entries(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ach_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ach_student_issued ON achievement_records(student_id, issued_at DESC);
CREATE INDEX idx_ach_project ON achievement_records(activity_project_id);
CREATE INDEX idx_ach_status ON achievement_records(status);
CREATE INDEX idx_ach_verification ON achievement_records(verification_code);
