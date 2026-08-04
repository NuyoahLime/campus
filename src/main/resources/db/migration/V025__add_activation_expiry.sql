-- V025: activation credential expiry
-- Add activation_issued_at and activation_expires_at columns to support
-- time-limited temporary credentials.

ALTER TABLE users
    ADD COLUMN activation_issued_at  timestamptz NULL,
    ADD COLUMN activation_expires_at timestamptz NULL;

-- Backfill existing PENDING_ACTIVATION rows with a 72-hour window
-- so their credentials remain usable.
UPDATE users
SET activation_issued_at  = now(),
    activation_expires_at = now() + INTERVAL '72 hours'
WHERE account_status = 'PENDING_ACTIVATION'
  AND activation_expires_at IS NULL;
