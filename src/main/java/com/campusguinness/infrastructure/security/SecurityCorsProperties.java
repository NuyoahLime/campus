package com.campusguinness.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS configuration for the API.
 * No default origins — must be explicitly configured.
 * Never use "*" with allow-credentials=true.
 */
@ConfigurationProperties(prefix = "campus-guinness.security.cors")
public class SecurityCorsProperties {

    private List<String> allowedOrigins = List.of();
    private long maxAge = 3600;

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> v) { this.allowedOrigins = v; }
    public long getMaxAge() { return maxAge; }
    public void setMaxAge(long v) { this.maxAge = v; }
}
