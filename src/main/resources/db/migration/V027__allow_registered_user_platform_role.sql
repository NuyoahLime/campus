ALTER TABLE users
    DROP CONSTRAINT chk_users_platform_role;

ALTER TABLE users
    ADD CONSTRAINT chk_users_platform_role
    CHECK (
        platform_role IS NULL
        OR platform_role IN ('SUPER_ADMIN', 'REGISTERED_USER')
    );
