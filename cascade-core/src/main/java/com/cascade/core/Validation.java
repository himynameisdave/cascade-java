package com.cascade.core;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bean-validation entry point. The factory is expensive to build, so it is
 * created once and shared; {@link Validator} is thread-safe.
 */
public final class Validation {

    private static final ValidatorFactory FACTORY =
            jakarta.validation.Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    /** Thrown when a request body fails bean validation. */
    public static class ValidationException extends RuntimeException {
        private final Map<String, String> details;

        public ValidationException(Map<String, String> details) {
            super("Validation failed");
            this.details = details;
        }

        public Map<String, String> details() {
            return details;
        }
    }

    private Validation() {
    }

    /** Validates {@code target}, throwing with every failing field at once. */
    public static <T> void check(T target) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(target);
        if (violations.isEmpty()) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        for (ConstraintViolation<T> violation : violations) {
            details.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        throw new ValidationException(details);
    }
}
