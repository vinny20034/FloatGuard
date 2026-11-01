package com.floatguard.error;

/**
 * The request is well-formed but conflicts with existing state (for example a
 * duplicate wallet type for an agent) -> HTTP 409 Conflict.
 *
 * <p>Note: {@code InsufficientFloatException} is deliberately <em>not</em> a
 * subclass of this type; it is a domain exception mapped to 409 separately by
 * the API layer.</p>
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(message);
    }
}
