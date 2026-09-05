-- V020: prevent duplicate active L3 authorization workflows while preserving withdrawn history.

CREATE UNIQUE INDEX uq_l3_auth_active_school_project_rule_scope
    ON l3_authorizations(school_id, project_id, rule_version_id, (COALESCE(data_scope::text, '{}')))
    WHERE authorization_status <> 'WITHDRAWN';
