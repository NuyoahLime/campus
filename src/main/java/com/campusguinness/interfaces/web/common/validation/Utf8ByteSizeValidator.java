package com.campusguinness.interfaces.web.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public final class Utf8ByteSizeValidator implements ConstraintValidator<Utf8ByteSize, String> {

    private int max;

    @Override
    public void initialize(Utf8ByteSize annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // @NotBlank handles null/blank
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
