package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * Thrown by {@link FloatWallet#applyOperation(Operation)} when an operation would
 * push a float below zero. The REST layer maps this to HTTP 409 Conflict.
 *
 * Unchecked on purpose: float exhaustion is a normal business outcome, not a
 * programming error, and callers higher up the stack should not be forced to
 * declare it.
 */
public class InsufficientFloatException extends RuntimeException {

    public InsufficientFloatException(String message) {
        super(message);
    }
}
