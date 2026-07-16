-- V010: result domain (activity_results, result_versions)
-- FK count: 5 (1 same-school composite + 2 cycle composite + 2 normal)

-- Step 1: activity_results (current_*_version_id NULL, FK added in Step 3)
CREATE TABLE activity_results (
    id                              uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id                       uuid          NOT NULL,
    activity_id                     uuid          NOT NULL,
    result_internal_status          varchar(32)   NOT NULL DEFAULT 'DRAFT',
    result_public_status            varchar(32)   NOT NULL DEFAULT 'NOT_SUBMITTED',
    current_internal_version_id     uuid          NULL,
    current_public_version_id       uuid          NULL,
    created_at                      timestamptz   NOT NULL DEFAULT now(),
    updated_at                      timestamptz   NOT NULL DEFAULT now(),
    version                         integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_result_activity UNIQUE (activity_id),
    CONSTRAINT chk_result_internal_status CHECK (
        result_internal_status IN ('DRAFT','INTERNAL_PUBLISHED','INTERNAL_WITHDRAWN')
    ),
    CONSTRAINT chk_result_public_status CHECK (
        result_public_status IN ('NOT_SUBMITTED','PENDING_PUBLIC_REVIEW','PLATFORM_APPROVED',
            'PLATFORM_REJECTED','PUBLIC','ANOMALY_PENDING','PLATFORM_TAKEDOWN')
    ),
    CONSTRAINT fk_result_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_activity_same_school FOREIGN KEY (activity_id, school_id)
        REFERENCES activities(id, school_id) ON DELETE RESTRICT
);

-- Step 2: result_versions + composite UKs for cycle FK
CREATE TABLE result_versions (
    id                          uuid PRIMARY KEY DEFAULT uuidv7(),
    result_id                   uuid          NOT NULL,
    version_number              integer       NOT NULL,
    title                       varchar(200)  NOT NULL,
    summary_text                text          NOT NULL,
    score_highlights            jsonb         NULL,
    media_refs                  jsonb         NULL,
    is_core_content_modified    boolean       NOT NULL DEFAULT true,
    format_change_log           text          NULL,
    published_internally_at     timestamptz   NULL,
    published_publicly_at       timestamptz   NULL,
    created_at                  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_result_version_result_num UNIQUE (result_id, version_number),
    CONSTRAINT uq_result_version_id_result UNIQUE (id, result_id),
    CONSTRAINT fk_result_version_result FOREIGN KEY (result_id)
        REFERENCES activity_results(id) ON DELETE RESTRICT
);

-- Step 3: activity_results current_*_version_id cycle FKs
ALTER TABLE activity_results
    ADD CONSTRAINT fk_result_current_internal_version
    FOREIGN KEY (current_internal_version_id, id)
    REFERENCES result_versions(id, result_id)
    ON DELETE RESTRICT;

ALTER TABLE activity_results
    ADD CONSTRAINT fk_result_current_public_version
    FOREIGN KEY (current_public_version_id, id)
    REFERENCES result_versions(id, result_id)
    ON DELETE RESTRICT;
