-- V001: identity users
-- FK count: 0

DO $$
DECLARE v integer;
BEGIN
  v := current_setting('server_version_num')::integer;
  IF v / 10000 <> 18 THEN
    RAISE EXCEPTION 'PostgreSQL major version 18 required, got %', current_setting('server_version');
  END IF;
END $$;

CREATE TABLE users (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    username        varchar(100)  NOT NULL,
    password_hash   varchar(255)  NOT NULL,
    account_status  varchar(32)   NOT NULL DEFAULT 'PENDING_ACTIVATION',
    platform_role   varchar(32)   NULL,
    locked_until    timestamptz   NULL,
    login_failures  integer       NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         integer       NOT NULL DEFAULT 1,

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_account_status CHECK (
        account_status IN ('PENDING_ACTIVATION','NORMAL','LOCKED','DISABLED')
    ),
    CONSTRAINT chk_users_platform_role CHECK (
        platform_role IS NULL OR platform_role = 'SUPER_ADMIN'
    )
);
