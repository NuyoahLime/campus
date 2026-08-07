package com.campusguinness.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * Exposes the password-first Campus authentication flow.
 */
@Configuration
public class AuthenticationProviderConfig {

    @Bean
    public AuthenticationManager authenticationManager(CampusAuthenticationProvider provider) {
        return new org.springframework.security.authentication.ProviderManager(provider);
    }
}
