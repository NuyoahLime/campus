package com.campusguinness.activity.application.command;

import java.time.Instant;

public record UpdateActivityCommand(String title, String description,
        Instant startTime, Instant endTime, String location) {}
