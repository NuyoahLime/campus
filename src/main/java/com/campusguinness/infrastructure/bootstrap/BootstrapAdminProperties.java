package com.campusguinness.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Environment-variable-only configuration for SUPER_ADMIN bootstrap.
 * No defaults — all values must be explicitly provided.
 * password is excluded from toString().
 */
@ConfigurationProperties(prefix = "campus-guinness.bootstrap-admin")
public class BootstrapAdminProperties {

    /** Must be explicitly set to "true" via CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_ENABLED. */
    private boolean enabled;

    /** Admin username from CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_USERNAME. No default. */
    private String username;

    /** Admin password from CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_PASSWORD. No default. */
    private String password;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    @Override
    public String toString() {
        return "BootstrapAdminProperties{enabled=" + enabled + ", username='" + username + "', password=[REDACTED]}";
    }
}
