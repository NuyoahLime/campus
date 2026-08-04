-- V026: verified email and secure auth token infrastructure
-- FK count: 2

ALTER TABLE users
    ADD COLUMN email VARCHAR(320),
    ADD COLUMN email_normalized VARCHAR(320),
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN registration_source VARCHAR(32)
        NOT NULL DEFAULT 'ADMIN_PROVISIONED';

ALTER TABLE users
    ADD CONSTRAINT chk_users_registration_source
    CHECK (
        registration_source IN (
            'ADMIN_PROVISIONED',
            'PUBLIC',
            'BOOTSTRAP'
        )
    );

CREATE UNIQUE INDEX uq_users_email_normalized
    ON users(email_normalized)
    WHERE email_normalized IS NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_email_consistency
    CHECK (
        (
            email IS NULL
            AND email_normalized IS NULL
            AND email_verified_at IS NULL
        )
        OR
        (
            email IS NOT NULL
            AND email_normalized IS NOT NULL
        )
    );

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    target_email_normalized VARCHAR(320) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_email_verification_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_email_verification_token_hash
        UNIQUE (token_hash),

    CONSTRAINT chk_email_verification_purpose
        CHECK (
            purpose IN (
                'PUBLIC_REGISTRATION',
                'RECOVERY_EMAIL'
            )
        ),

    CONSTRAINT chk_email_verification_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT chk_email_verification_used
        CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX idx_email_verification_tokens_user
    ON email_verification_tokens(user_id);

CREATE INDEX idx_email_verification_tokens_expires
    ON email_verification_tokens(expires_at);

CREATE INDEX idx_email_verification_tokens_active_user
    ON email_verification_tokens(user_id, purpose)
    WHERE used_at IS NULL;

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    requested_ip VARCHAR(64),
    version INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_password_reset_token_hash
        UNIQUE (token_hash),

    CONSTRAINT chk_password_reset_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT chk_password_reset_used
        CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX idx_password_reset_tokens_user
    ON password_reset_tokens(user_id);

CREATE INDEX idx_password_reset_tokens_expires
    ON password_reset_tokens(expires_at);

CREATE INDEX idx_password_reset_tokens_active_user
    ON password_reset_tokens(user_id)
    WHERE used_at IS NULL;
