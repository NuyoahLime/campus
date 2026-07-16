package com.campusguinness.interfaces.web.school;

import java.util.UUID;

/** TEMPORARY_EXPLICIT_ACTOR_ID — actorId will be sourced from security context once authentication is implemented. */
public record ActivateSchoolRequest(UUID actorId) {}
