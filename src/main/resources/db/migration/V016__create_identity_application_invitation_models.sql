-- V016: identity application and invitation models
-- FK count: 5

CREATE TABLE student_identity_applications (
    id                    uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id               uuid          NOT NULL,
    school_id             uuid          NOT NULL,
    real_name             varchar(100)  NOT NULL,
    student_number        varchar(64)   NOT NULL,
    grade                 varchar(32)   NOT NULL,
    class_name            varchar(64)   NOT NULL,
    evidence_file_key     varchar(500)  NULL,
    application_status    varchar(32)   NOT NULL DEFAULT 'PENDING',
    reviewed_by           uuid          NULL,
    reviewed_at           timestamptz   NULL,
    rejection_reason      text          NULL,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    version               integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_student_identity_app_status CHECK (
        application_status IN ('PENDING','APPROVED','REJECTED')
    ),
    CONSTRAINT chk_student_identity_app_review CHECK (
        (application_status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (application_status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT chk_student_identity_app_rejection_reason CHECK (
        (application_status = 'REJECTED' AND rejection_reason IS NOT NULL)
        OR (application_status <> 'REJECTED' AND rejection_reason IS NULL)
    ),
    CONSTRAINT fk_student_identity_app_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_identity_app_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_identity_app_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_pending_student_identity_application
    ON student_identity_applications(user_id, school_id)
    WHERE application_status = 'PENDING';

CREATE INDEX idx_student_identity_app_school_status
    ON student_identity_applications(school_id, application_status);

CREATE TABLE school_admin_invitations (
    id                    uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id               uuid          NOT NULL,
    school_id             uuid          NOT NULL,
    role_in_school        varchar(32)   NOT NULL DEFAULT 'SCHOOL_ADMIN',
    invitation_code_hash  varchar(255)  NOT NULL,
    invitation_status     varchar(32)   NOT NULL DEFAULT 'PENDING',
    expires_at            timestamptz   NOT NULL,
    accepted_at           timestamptz   NULL,
    revoked_at            timestamptz   NULL,
    created_by            uuid          NOT NULL,
    failed_attempts       integer       NOT NULL DEFAULT 0,
    max_attempts          integer       NOT NULL DEFAULT 5,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    version               integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_school_admin_inv_role CHECK (
        role_in_school = 'SCHOOL_ADMIN'
    ),
    CONSTRAINT chk_school_admin_inv_status CHECK (
        invitation_status IN ('PENDING','ACCEPTED','REVOKED','EXPIRED')
    ),
    CONSTRAINT chk_school_admin_inv_terminal_timestamps CHECK (
        (invitation_status = 'ACCEPTED' AND accepted_at IS NOT NULL AND revoked_at IS NULL)
        OR (invitation_status = 'REVOKED' AND revoked_at IS NOT NULL AND accepted_at IS NULL)
        OR (invitation_status IN ('PENDING','EXPIRED') AND accepted_at IS NULL AND revoked_at IS NULL)
    ),
    CONSTRAINT chk_school_admin_inv_attempts CHECK (
        max_attempts > 0 AND failed_attempts >= 0 AND failed_attempts <= max_attempts
    ),
    CONSTRAINT fk_school_admin_inv_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_admin_inv_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_admin_inv_created_by FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_pending_school_admin_invitation
    ON school_admin_invitations(user_id, school_id, role_in_school)
    WHERE invitation_status = 'PENDING';

CREATE INDEX idx_school_admin_inv_school_status
    ON school_admin_invitations(school_id, invitation_status);
