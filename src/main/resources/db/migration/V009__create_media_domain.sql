-- V009: media domain (media, media_review_records)
-- FK count: 4 (1 same-school composite + 3 normal)

CREATE TABLE media (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    school_id       uuid          NOT NULL,
    activity_id     uuid          NOT NULL,
    uploader_id     uuid          NOT NULL,
    file_key        varchar(500)  NOT NULL,
    file_name       varchar(300)  NOT NULL,
    file_type       varchar(16)   NOT NULL,
    file_format     varchar(16)   NOT NULL,
    file_size_bytes bigint        NOT NULL,
    checksum        varchar(128)  NULL,
    internal_status varchar(32)   NOT NULL DEFAULT 'DRAFT',
    public_status   varchar(32)   NOT NULL DEFAULT 'NOT_SUBMITTED',
    description     text          NULL,
    uploaded_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         integer       NOT NULL DEFAULT 1,

    CONSTRAINT chk_media_file_type CHECK (file_type IN ('IMAGE','VIDEO')),
    CONSTRAINT chk_media_internal_status CHECK (
        internal_status IN ('DRAFT','PENDING_INTERNAL_REVIEW','INTERNAL_APPROVED',
            'INTERNAL_REJECTED','INTERNAL_DISABLED')
    ),
    CONSTRAINT chk_media_public_status CHECK (
        public_status IN ('NOT_SUBMITTED','PENDING_PUBLIC_REVIEW','PLATFORM_APPROVED',
            'PLATFORM_REJECTED','PUBLIC','PLATFORM_TAKEDOWN')
    ),
    CONSTRAINT fk_media_school FOREIGN KEY (school_id)
        REFERENCES schools(id) ON DELETE RESTRICT,
    CONSTRAINT fk_media_activity_same_school FOREIGN KEY (activity_id, school_id)
        REFERENCES activities(id, school_id) ON DELETE RESTRICT,
    CONSTRAINT fk_media_uploader FOREIGN KEY (uploader_id)
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_media_activity ON media(activity_id);
CREATE INDEX idx_media_uploader ON media(uploader_id);

CREATE TABLE media_review_records (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    media_id        uuid          NOT NULL,
    review_level    varchar(16)   NOT NULL,
    reviewer_id     uuid          NOT NULL,
    review_result   varchar(32)   NOT NULL,
    review_comment  text          NULL,
    reject_reason   text          NULL,
    reviewed_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT chk_media_review_level CHECK (review_level IN ('INTERNAL','PUBLIC')),
    CONSTRAINT chk_media_review_result CHECK (review_result IN ('APPROVED','REJECTED')),
    CONSTRAINT fk_media_review_media FOREIGN KEY (media_id)
        REFERENCES media(id) ON DELETE RESTRICT,
    CONSTRAINT fk_media_review_reviewer FOREIGN KEY (reviewer_id)
        REFERENCES users(id) ON DELETE RESTRICT
);
