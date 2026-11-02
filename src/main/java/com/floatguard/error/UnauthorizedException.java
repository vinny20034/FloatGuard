package com.floatguard.error;

/**
 * Raised when a request is not authenticated (missing, unknown or expired
 * session token). Mapped to HTTP 401 by the API layer.
 */
public class UnauthorizedException extends AppException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
