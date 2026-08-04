package com.campusguinness.identity.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

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
        publicFrontendUrl = validatePublicFrontendUrl(trimTrailingSlash(publicFrontendUrl));
    }

    private static String trimTrailingSlash(String value) {
        var trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String validatePublicFrontendUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("publicFrontendUrl must be an absolute HTTP(S) URL", e);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("publicFrontendUrl must be an absolute HTTP(S) URL without query, fragment or user-info");
        }
        return value;
    }
}
