package com.campusguinness.interfaces.web.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a String's UTF-8 byte length does not exceed {@link #max()}.
 * Stricter than {@code @Size} for multibyte characters:
 * e.g., "😀" is length=2 in Java but 4 bytes in UTF-8.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Utf8ByteSizeValidator.class)
@Documented
public @interface Utf8ByteSize {

    String message() default "must not exceed {max} UTF-8 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int max();
}
