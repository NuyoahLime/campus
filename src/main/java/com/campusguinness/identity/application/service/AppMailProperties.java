package com.campusguinness.identity.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        String from,
        String publicFrontendUrl
) {
    public AppMailProperties {
        if (from == null || from.isBlank()) {
            from = "no-reply@campus-guinness.local";
        }
        if (publicFrontendUrl == null || publicFrontendUrl.isBlank()) {
            publicFrontendUrl = "http://localhost:5173";
        }
        publicFrontendUrl = trimTrailingSlash(publicFrontendUrl);
    }

    private static String trimTrailingSlash(String value) {
        var trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
