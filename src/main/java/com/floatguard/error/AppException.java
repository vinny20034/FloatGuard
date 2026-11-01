package com.floatguard.error;

import java.util.Map;

/**
 * Base type for "expected" failures that the REST layer knows how to translate
 * into a specific HTTP status. Living in its own package keeps the layering
 * clean: both the service layer and the API layer may depend on it, while it
 * depends on nothing.
 */
public class AppException extends RuntimeException {

    private final Map<String, String> details;

    public AppException(String message) {
        this(message, Map.of());
    }

    public AppException(String message, Map<String, String> details) {
        super(message);
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** Field-level context for validation errors (may be empty). */
    public Map<String, String> getDetails() {
        return details;
    }
}
