package com.campusguinness.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

public class LoginDeniedAuthenticationException extends AuthenticationException {

    private final String code;
    private final HttpStatus status;

    public LoginDeniedAuthenticationException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
