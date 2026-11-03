package com.floatguard.model.integration;

/**
 * Thrown by {@link FloatWallet#applyOperation(Operation)} when an operation would
 * push a float below zero. The REST layer maps this to HTTP 409 Conflict.
 *
 * Unchecked on purpose: float exhaustion is a normal business outcome, not a
 * programming error, so callers are not forced to declare or catch it.
 */
public class InsufficientFloatException extends RuntimeException {

    public InsufficientFloatException(String message) {
        super(message);
    }

    public InsufficientFloatException(String message, Throwable cause) {
        super(message, cause);
    }
}
