package com.floatguard.error;

import java.util.Map;

/** Invalid or missing input -> HTTP 400 Bad Request. */
public class ValidationException extends AppException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Map<String, String> details) {
        super(message, details);
    }
}
