-- V017: ranking version withdrawal support

ALTER TABLE ranking_versions
    ADD COLUMN IF NOT EXISTS withdrawn_at      timestamptz NULL,
    ADD COLUMN IF NOT EXISTS withdrawn_by      uuid         NULL,
    ADD COLUMN IF NOT EXISTS withdrawal_reason text         NULL;
