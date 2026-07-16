-- V007: ranking domain (ranking_definitions, ranking_versions, ranking_entries, ranking_entry_score_sources, l3_authorizations)
-- FK count: 18 (2 cycle composite + 2 same-student composite + 2 same-project composite + 12 normal)

-- Step 1: ranking_definitions (current_version_id NULL, FK added in Step 3)
CREATE TABLE ranking_definitions (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    layer               varchar(8)    NOT NULL,
    name                varchar(200)  NOT NULL,
    school_id           uuid          NULL,
    project_id          uuid          NOT NULL,
    dimension_filters   jsonb         NULL,
    tie_break_rule      varchar(32)   NULL,
    is_enabled          boolean       NOT NULL DEFAULT true,
    current_version_id  uuid          NULL,
    created_by          uuid          NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    version             integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_ranking_layer CHECK (layer IN ('L1','L2','L3')),
    CONSTRAINT fk_ranking_def_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ranking_def_project FOREIGN KEY (project_id)
        REFERENCES challenge_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ranking_def_creator FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

-- Step 2: ranking_versions + composite UKs
CREATE TABLE ranking_versions (
    id                          uuid PRIMARY KEY DEFAULT uuidv7(),
    definition_id               uuid          NOT NULL,
    version_number              integer       NOT NULL,
    previous_version_id         uuid          NULL,
    version_status              varchar(32)   NOT NULL DEFAULT 'DRAFT_CALC',
    calculation_params          jsonb         NULL,
    data_scope_snapshot         jsonb         NULL,
    authorization_ids_snapshot  jsonb         NULL,
    generated_at                timestamptz   NULL,
    published_at                timestamptz   NULL,
    withdrawn_at                timestamptz   NULL,
    created_reason              varchar(64)   NULL,
    created_at                  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_ranking_version_def_num UNIQUE (definition_id, version_number),
    CONSTRAINT uq_ranking_version_id_def UNIQUE (id, definition_id),
    CONSTRAINT chk_ranking_version_status CHECK (
        version_status IN ('DRAFT_CALC','GENERATED','PUBLISHED','WITHDRAWN','EXPIRED','REPLACED','VOIDED')
    ),
    CONSTRAINT fk_ranking_version_def FOREIGN KEY (definition_id)
        REFERENCES ranking_definitions(id) ON DELETE RESTRICT
);

CREATE TABLE ranking_entries (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    version_id              uuid          NOT NULL,
    student_id              uuid          NOT NULL,
    rank_position           integer       NOT NULL,
    student_display_name    varchar(200)  NOT NULL,
    school_name             varchar(200)  NULL,
    score_display_value     varchar(100)  NOT NULL,
    rule_version_id         uuid          NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_ranking_entry_id_student UNIQUE (id, student_id),
    CONSTRAINT uq_ranking_entry_version_student UNIQUE (version_id, student_id),
    CONSTRAINT fk_ranking_entry_version FOREIGN KEY (version_id)
        REFERENCES ranking_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ranking_entry_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ranking_entry_rule_version FOREIGN KEY (rule_version_id)
        REFERENCES project_rule_versions(id) ON DELETE RESTRICT
);

CREATE TABLE ranking_entry_score_sources (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    entry_id            uuid          NOT NULL,
    student_id          uuid          NOT NULL,
    score_attempt_id    uuid          NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_entry_score_source UNIQUE (entry_id, score_attempt_id),
    CONSTRAINT fk_ress_entry FOREIGN KEY (entry_id, student_id)
        REFERENCES ranking_entries(id, student_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ress_score FOREIGN KEY (score_attempt_id, student_id)
        REFERENCES score_attempts(id, student_id) ON DELETE RESTRICT
);

CREATE TABLE l3_authorizations (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id               uuid          NOT NULL,
    project_id              uuid          NOT NULL,
    rule_version_id         uuid          NOT NULL,
    data_scope              jsonb         NULL,
    allow_school_name       boolean       NOT NULL DEFAULT true,
    allow_student_name      boolean       NOT NULL DEFAULT false,
    authorization_status    varchar(32)   NOT NULL DEFAULT 'DRAFT',
    submitted_at            timestamptz   NULL,
    reviewed_by             uuid          NULL,
    reviewed_at             timestamptz   NULL,
    review_comment          text          NULL,
    reject_reason           text          NULL,
    paused_at               timestamptz   NULL,
    withdrawn_at            timestamptz   NULL,
    withdraw_reason         text          NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    version                 integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_l3_auth_status CHECK (
        authorization_status IN ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED','SUSPENDED','WITHDRAWN')
    ),
    CONSTRAINT fk_l3_auth_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_l3_auth_project FOREIGN KEY (project_id)
        REFERENCES challenge_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_l3_auth_rule_version FOREIGN KEY (rule_version_id, project_id)
        REFERENCES project_rule_versions(id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_l3_auth_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

-- Step 3: ranking_definitions current_version_id cycle FK
ALTER TABLE ranking_definitions
    ADD CONSTRAINT fk_ranking_def_current_version
    FOREIGN KEY (current_version_id, id)
    REFERENCES ranking_versions(id, definition_id)
    ON DELETE RESTRICT;
