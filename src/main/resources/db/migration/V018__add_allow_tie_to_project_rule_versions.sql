-- V018: rule version snapshots must preserve the complete score configuration.
ALTER TABLE project_rule_versions
    ADD COLUMN allow_tie boolean NOT NULL DEFAULT true;
