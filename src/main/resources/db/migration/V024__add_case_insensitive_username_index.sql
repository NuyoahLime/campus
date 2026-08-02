-- V024: case-insensitive username uniqueness
-- Adds a unique index on lower(username) to prevent Alice/alice conflicts.
-- The existing uq_users_username enforces case-sensitive uniqueness;
-- this index adds case-insensitive protection.
CREATE UNIQUE INDEX uq_users_username_ci ON users (lower(username));
