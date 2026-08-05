package com.campusguinness.identity.application.service;

enum ActivationOutcome {
    SUCCESS,
    INVALID_CREDENTIAL,
    EXPIRED,
    ACCOUNT_NOT_ACTIVATABLE,
    MEMBERSHIP_CONFLICT
}
