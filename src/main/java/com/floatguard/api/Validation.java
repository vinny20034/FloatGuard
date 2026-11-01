package com.floatguard.api;

import com.floatguard.error.ValidationException;

import java.util.Map;

/**
 * Small guard-clause helpers used by the routes. They collect every problem into
 * one map so the client receives all validation failures in a single 400 response
 * instead of fixing them one at a time.
 */
public final class Validation {

    private Validation() {
    }

    public static void requireNonBlank(Map<String, String> errors, String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            errors.put(field, field + " is required and must not be blank.");
        }
    }

    public static void requirePositiveAmount(Map<String, String> errors, String field, Double value) {
        if (value == null) {
            errors.put(field, field + " is required.");
            return;
        }
        if (!Double.isFinite(value) || value <= 0) {
            errors.put(field, field + " must be a number greater than zero.");
        }
    }

    /** Throws a 400 if any field failed validation. */
    public static void failIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException("Request validation failed.", errors);
        }
    }
}
