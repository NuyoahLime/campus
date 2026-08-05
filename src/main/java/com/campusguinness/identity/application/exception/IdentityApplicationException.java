package com.campusguinness.identity.application.exception;

public class IdentityApplicationException extends RuntimeException {

    private final String code;

    public IdentityApplicationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
