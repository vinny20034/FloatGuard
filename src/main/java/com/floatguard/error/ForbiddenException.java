package com.floatguard.error;

/**
 * Raised when an authenticated user is not allowed to perform an action
 * (wrong role, or reaching another agent's data). Mapped to HTTP 403.
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(message);
    }
}
