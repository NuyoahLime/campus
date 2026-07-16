-- V006: score domain (score_attempts, score_review_records, score_correction_records, abnormal_score_entries)
-- FK count: 16 (1 abnormal composite + 15 normal)

CREATE TABLE score_attempts (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id               uuid          NOT NULL,
    activity_project_id     uuid          NOT NULL,
    student_id              uuid          NOT NULL,
    attempt_number          integer       NOT NULL,
    score_storage_type      varchar(32)   NOT NULL,
    score_value             decimal(18,4) NULL,
    score_duration_ms       bigint        NULL,
    score_grade             varchar(32)   NULL,
    score_business_time     timestamptz   NULL,
    time_source             varchar(32)   NULL,
    is_current_effective    boolean       NOT NULL DEFAULT false,
    replaces_id             uuid          NULL,
    score_status            varchar(32)   NOT NULL DEFAULT 'DRAFT',
    entered_by              uuid          NOT NULL,
    submitted_at            timestamptz   NULL,
    is_manual_makeup        boolean       NOT NULL DEFAULT false,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    version                 integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_score_attempt_id_student UNIQUE (id, student_id),
    CONSTRAINT uq_score_attempt_id_school_ap_student UNIQUE (id, school_id, activity_project_id, student_id),
    CONSTRAINT uq_score_attempt_ap_student_num UNIQUE (activity_project_id, student_id, attempt_number),
    CONSTRAINT chk_score_status CHECK (
        score_status IN ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED','INVALIDATED')
    ),
    CONSTRAINT chk_score_storage_type CHECK (
        score_storage_type IN ('INTEGER','DECIMAL','DURATION','GRADE')
    ),
    CONSTRAINT chk_score_value_mutex CHECK (
        (score_storage_type = 'DURATION' AND score_duration_ms IS NOT NULL AND score_duration_ms >= 0
         AND score_value IS NULL AND score_grade IS NULL)
        OR
        (score_storage_type = 'INTEGER' AND score_value IS NOT NULL AND score_value = floor(score_value)
         AND score_duration_ms IS NULL AND score_grade IS NULL)
        OR
        (score_storage_type = 'DECIMAL' AND score_value IS NOT NULL
         AND score_duration_ms IS NULL AND score_grade IS NULL)
        OR
        (score_storage_type = 'GRADE' AND score_grade IS NOT NULL
         AND score_duration_ms IS NULL AND score_value IS NULL)
    ),
    CONSTRAINT fk_score_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_score_activity_project FOREIGN KEY (activity_project_id)
        REFERENCES activity_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_score_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_score_entered_by FOREIGN KEY (entered_by)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_score_replaces FOREIGN KEY (replaces_id)
        REFERENCES score_attempts(id) ON DELETE RESTRICT
);

-- EffectiveScore: partial unique index
CREATE UNIQUE INDEX uq_effective_score
    ON score_attempts(student_id, activity_project_id)
    WHERE is_current_effective = true;

CREATE TABLE score_review_records (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    score_attempt_id    uuid          NOT NULL,
    reviewer_id         uuid          NOT NULL,
    review_result       varchar(32)   NOT NULL,
    review_comment      text          NULL,
    reject_reason       text          NULL,
    reviewed_at         timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT chk_review_result CHECK (
        review_result IN ('APPROVED','REJECTED')
    ),
    CONSTRAINT fk_review_score FOREIGN KEY (score_attempt_id)
        REFERENCES score_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE score_correction_records (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    original_score_id   uuid          NOT NULL,
    new_score_id        uuid          NOT NULL,
    correction_reason   text          NOT NULL,
    corrected_by        uuid          NOT NULL,
    corrected_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_correction_new_score UNIQUE (new_score_id),
    CONSTRAINT chk_correction_no_self CHECK (original_score_id <> new_score_id),
    CONSTRAINT fk_correction_original FOREIGN KEY (original_score_id)
        REFERENCES score_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_new FOREIGN KEY (new_score_id)
        REFERENCES score_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_corrected_by FOREIGN KEY (corrected_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE abnormal_score_entries (
    id                          uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id                   uuid          NOT NULL,
    activity_project_id         uuid          NOT NULL,
    student_id                  uuid          NOT NULL,
    make_up_reason              text          NOT NULL,
    evidence_file_keys          jsonb         NULL,
    entry_status                varchar(32)   NOT NULL DEFAULT 'DRAFT',
    initiator_id                uuid          NOT NULL,
    approver_id                 uuid          NULL,
    approval_comment            text          NULL,
    reject_reason               text          NULL,
    generated_score_attempt_id  uuid          NULL,
    application_version         integer       NOT NULL DEFAULT 1,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    version                     integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_abnormal_generated_score UNIQUE (generated_score_attempt_id),
    CONSTRAINT chk_abnormal_status CHECK (
        entry_status IN ('DRAFT','PENDING_APPROVAL','REJECTED','APPROVED_PENDING_ENTRY',
            'SCORE_REVIEWING','COMPLETED','TERMINATED')
    ),
    CONSTRAINT chk_abnormal_completed CHECK (
        entry_status <> 'COMPLETED' OR generated_score_attempt_id IS NOT NULL
    ),
    CONSTRAINT fk_abnormal_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_abnormal_activity_project FOREIGN KEY (activity_project_id)
        REFERENCES activity_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_abnormal_student FOREIGN KEY (student_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_abnormal_initiator FOREIGN KEY (initiator_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_abnormal_approver FOREIGN KEY (approver_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_abnormal_generated_score FOREIGN KEY (
        generated_score_attempt_id, school_id, activity_project_id, student_id
    ) REFERENCES score_attempts(id, school_id, activity_project_id, student_id)
        ON DELETE RESTRICT
);
