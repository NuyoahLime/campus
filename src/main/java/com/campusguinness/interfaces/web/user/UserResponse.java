package com.campusguinness.interfaces.web.user;

import java.util.UUID;

public record UserResponse(UUID id, String username, String status) {}
