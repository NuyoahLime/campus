-- V002: school domain (schools, school_registrations)
-- FK count: 2

CREATE TABLE schools (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    name            varchar(200)  NOT NULL,
    unified_code_type varchar(32)  NOT NULL,
    unified_code    varchar(64)   NOT NULL,
    internal_code   varchar(32)   NOT NULL,
    school_type     varchar(32)   NOT NULL,
    region          varchar(128)  NOT NULL,
    address         text          NOT NULL,
    contact_name    varchar(100)  NOT NULL,
    contact_phone   varchar(32)   NOT NULL,
    contact_email   varchar(200)  NOT NULL,
    school_status   varchar(32)   NOT NULL DEFAULT 'PENDING_ENABLE',
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_schools_unified_code UNIQUE (unified_code_type, unified_code),
    CONSTRAINT uq_schools_internal_code UNIQUE (internal_code),
    CONSTRAINT chk_schools_school_status CHECK (
        school_status IN ('PENDING_ENABLE','NORMAL','SUSPENDED','DISABLED')
    )
);

CREATE TABLE school_registrations (
    id                    uuid PRIMARY KEY DEFAULT uuidv7(),
    school_name           varchar(200)  NOT NULL,
    unified_code_type     varchar(32)   NOT NULL,
    unified_code          varchar(64)   NULL,
    school_type           varchar(32)   NOT NULL,
    region                varchar(128)  NOT NULL,
    address               text          NOT NULL,
    contact_name          varchar(100)  NOT NULL,
    contact_phone         varchar(32)   NOT NULL,
    contact_email         varchar(200)  NOT NULL,
    description           text          NULL,
    evidence_file_key     varchar(500)  NULL,
    registration_status   varchar(32)   NOT NULL DEFAULT 'DRAFT',
    created_school_id     uuid          NULL,
    reviewed_by           uuid          NULL,
    reviewed_at           timestamptz   NULL,
    review_comment        text          NULL,
    reject_reason         text          NULL,
    withdrawn_by          varchar(100)  NULL,
    withdrawn_at          timestamptz   NULL,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    version               integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_school_registrations_status CHECK (
        registration_status IN ('DRAFT','SUBMITTED','NEED_SUPPLEMENT','APPROVED','REJECTED','WITHDRAWN')
    ),
    CONSTRAINT fk_school_reg_created_school FOREIGN KEY (created_school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_reg_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES users(id) ON DELETE RESTRICT
);
