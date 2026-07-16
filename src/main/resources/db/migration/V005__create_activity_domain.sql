-- V005: activity domain (applications, activities, activity_projects, responsible_teachers, activity_participants)
-- FK count: 12 (1 same-school composite + 1 composite rule_version + 10 normal)

CREATE TABLE activity_applications (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id           uuid          NOT NULL,
    applicant_id        uuid          NOT NULL,
    title               varchar(200)  NOT NULL,
    description         text          NULL,
    application_status  varchar(32)   NOT NULL DEFAULT 'DRAFT',
    created_activity_id uuid          NULL,
    reviewed_by         uuid          NULL,
    reviewed_at         timestamptz   NULL,
    review_comment      text          NULL,
    reject_reason       text          NULL,
    application_version integer       NOT NULL DEFAULT 1,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    version             integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_app_created_activity UNIQUE (created_activity_id),
    CONSTRAINT chk_app_status CHECK (
        application_status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN')
    ),
    CONSTRAINT fk_app_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_applicant FOREIGN KEY (applicant_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE activities (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id       uuid          NOT NULL,
    title           varchar(200)  NOT NULL,
    description     text          NULL,
    start_time      timestamptz   NULL,
    end_time        timestamptz   NULL,
    location        varchar(300)  NULL,
    execution_status varchar(32)  NOT NULL DEFAULT 'DRAFT',
    public_status   varchar(32)   NOT NULL DEFAULT 'NOT_SUBMITTED',
    created_by      uuid          NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_activity_id_school UNIQUE (id, school_id),
    CONSTRAINT chk_activity_exec_status CHECK (
        execution_status IN ('DRAFT','PUBLISHED','IN_PROGRESS','ENDED','CANCELLED')
    ),
    CONSTRAINT chk_activity_public_status CHECK (
        public_status IN ('NOT_SUBMITTED','PENDING_PLATFORM_REVIEW','PLATFORM_APPROVED',
            'PLATFORM_REJECTED','PUBLIC','SCHOOL_WITHDRAWN','PLATFORM_TAKEDOWN')
    ),
    CONSTRAINT fk_activity_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_creator FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

-- activity_applications -> activities same-school composite FK
ALTER TABLE activity_applications
    ADD CONSTRAINT fk_app_created_activity_same_school
    FOREIGN KEY (created_activity_id, school_id)
    REFERENCES activities(id, school_id)
    ON DELETE RESTRICT;

CREATE TABLE activity_projects (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    activity_id     uuid          NOT NULL,
    project_id      uuid          NOT NULL,
    rule_version_id uuid          NOT NULL,
    config          jsonb         NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_activity_project UNIQUE (activity_id, project_id),
    CONSTRAINT fk_ap_activity FOREIGN KEY (activity_id)
        REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ap_project FOREIGN KEY (project_id)
        REFERENCES challenge_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ap_rule_version FOREIGN KEY (rule_version_id, project_id)
        REFERENCES project_rule_versions(id, project_id) ON DELETE RESTRICT
);

CREATE TABLE responsible_teachers (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    activity_project_id     uuid          NOT NULL,
    teacher_membership_id   uuid          NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_responsible_teacher UNIQUE (activity_project_id, teacher_membership_id),
    CONSTRAINT fk_rt_activity_project FOREIGN KEY (activity_project_id)
        REFERENCES activity_projects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_rt_teacher_membership FOREIGN KEY (teacher_membership_id)
        REFERENCES school_memberships(id) ON DELETE RESTRICT
);

CREATE TABLE activity_participants (
    id                      uuid PRIMARY KEY DEFAULT uuidv7(),
    activity_id             uuid          NOT NULL,
    student_membership_id   uuid          NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_activity_participant UNIQUE (activity_id, student_membership_id),
    CONSTRAINT fk_apart_activity FOREIGN KEY (activity_id)
        REFERENCES activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_apart_student_membership FOREIGN KEY (student_membership_id)
        REFERENCES school_memberships(id) ON DELETE RESTRICT
);
