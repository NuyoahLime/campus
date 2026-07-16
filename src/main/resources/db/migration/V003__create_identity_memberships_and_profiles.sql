-- V003: identity memberships and profiles
-- FK count: 4

CREATE TABLE school_memberships (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id         uuid          NOT NULL,
    school_id       uuid          NOT NULL,
    role_in_school  varchar(32)   NOT NULL,
    status          varchar(32)   NOT NULL DEFAULT 'ACTIVE',
    started_at      timestamptz   NOT NULL DEFAULT now(),
    ended_at        timestamptz   NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    version         integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_membership_role CHECK (
        role_in_school IN ('STUDENT','TEACHER','SCHOOL_ADMIN')
    ),
    CONSTRAINT chk_membership_status CHECK (
        status IN ('ACTIVE','ENDED')
    ),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_active_membership
    ON school_memberships(user_id, school_id)
    WHERE status = 'ACTIVE';

CREATE TABLE student_profiles (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    membership_id   uuid          NOT NULL,
    grade           varchar(32)   NULL,
    class_name      varchar(64)   NULL,
    student_number  varchar(64)   NULL,

    CONSTRAINT uq_student_profile_membership UNIQUE (membership_id),
    CONSTRAINT fk_student_profile_membership FOREIGN KEY (membership_id)
        REFERENCES school_memberships(id) ON DELETE RESTRICT
);

CREATE TABLE teacher_profiles (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    membership_id   uuid          NOT NULL,
    subject         varchar(64)   NULL,
    title           varchar(64)   NULL,

    CONSTRAINT uq_teacher_profile_membership UNIQUE (membership_id),
    CONSTRAINT fk_teacher_profile_membership FOREIGN KEY (membership_id)
        REFERENCES school_memberships(id) ON DELETE RESTRICT
);
