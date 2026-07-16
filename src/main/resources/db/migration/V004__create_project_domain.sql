-- V004: project domain (challenge_projects, project_rule_versions, project_rule_compatibilities)
-- FK count: 5 (1 composite cycle + 2 normal + 2 composite)

-- Step 1: challenge_projects (current_rule_version_id NULL, FK added in Step 3)
CREATE TABLE challenge_projects (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    name                    varchar(200)  NOT NULL,
    category                varchar(64)   NOT NULL,
    description             text          NULL,
    venue_requirements      text          NULL,
    equipment_requirements  text          NULL,
    rules_text              text          NULL,
    score_storage_type      varchar(32)   NOT NULL,
    score_indicator_type    varchar(32)   NOT NULL,
    comparison_direction    varchar(32)   NOT NULL,
    score_unit              varchar(32)   NULL,
    decimal_places          integer       NULL,
    grade_order             text          NULL,
    allow_tie               boolean       NOT NULL DEFAULT true,
    effective_score_rule    varchar(32)   NOT NULL DEFAULT 'BEST',
    project_status          varchar(32)   NOT NULL DEFAULT 'DRAFT',
    current_rule_version_id uuid          NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    version                 integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_project_score_storage_type CHECK (
        score_storage_type IN ('INTEGER','DECIMAL','DURATION','GRADE')
    ),
    CONSTRAINT chk_project_comparison_direction CHECK (
        comparison_direction IN ('HIGHER_BETTER','LOWER_BETTER','GRADE_ORDER','NO_RANKING')
    ),
    CONSTRAINT chk_project_effective_score_rule CHECK (
        effective_score_rule IN ('BEST','LAST','ADMIN_DESIGNATED')
    ),
    CONSTRAINT chk_project_status CHECK (
        project_status IN ('DRAFT','PUBLISHED','ARCHIVED')
    )
);

-- Step 2: project_rule_versions + composite UK for cycle FK
CREATE TABLE project_rule_versions (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    project_id              uuid          NOT NULL,
    version_number          integer       NOT NULL,
    score_storage_type      varchar(32)   NOT NULL,
    score_indicator_type    varchar(32)   NOT NULL,
    comparison_direction    varchar(32)   NOT NULL,
    score_unit              varchar(32)   NULL,
    decimal_places          integer       NULL,
    grade_order             text          NULL,
    rules_text              text          NULL,
    venue_requirements      text          NULL,
    equipment_requirements  text          NULL,
    effective_score_rule    varchar(32)   NOT NULL,
    change_reason           text          NULL,
    created_by              uuid          NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_rule_version_project_num UNIQUE (project_id, version_number),
    CONSTRAINT uq_rule_version_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_rule_version_project FOREIGN KEY (project_id)
        REFERENCES challenge_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rule_version_creator FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE project_rule_compatibilities (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    project_id              uuid          NOT NULL,
    source_id               uuid          NOT NULL,
    target_id               uuid          NOT NULL,
    compatibility_status    varchar(32)   NOT NULL DEFAULT 'COMPATIBLE',
    decision_reason         text          NOT NULL,
    decided_by              uuid          NOT NULL,
    decided_at              timestamptz   NOT NULL DEFAULT now(),
    invalidated_at          timestamptz   NULL,

    CONSTRAINT chk_compat_status CHECK (
        compatibility_status IN ('COMPATIBLE','INCOMPATIBLE','INVALIDATED')
    ),
    CONSTRAINT chk_compat_no_self CHECK (source_id <> target_id),
    CONSTRAINT chk_compat_order CHECK (source_id < target_id),
    CONSTRAINT uq_compat_project_pair UNIQUE (project_id, source_id, target_id),
    CONSTRAINT fk_compat_project FOREIGN KEY (project_id)
        REFERENCES challenge_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_compat_source FOREIGN KEY (source_id, project_id)
        REFERENCES project_rule_versions(id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_compat_target FOREIGN KEY (target_id, project_id)
        REFERENCES project_rule_versions(id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_compat_decided_by FOREIGN KEY (decided_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

-- Step 3: challenge_projects current_rule_version_id cycle FK
ALTER TABLE challenge_projects
    ADD CONSTRAINT fk_project_current_rule_version
    FOREIGN KEY (current_rule_version_id, id)
    REFERENCES project_rule_versions(id, project_id)
    ON DELETE RESTRICT;
