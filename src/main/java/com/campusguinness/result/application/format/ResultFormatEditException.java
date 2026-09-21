package com.campusguinness.result.application.format;

public class ResultFormatEditException extends RuntimeException {
    private final String code;
    public ResultFormatEditException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
