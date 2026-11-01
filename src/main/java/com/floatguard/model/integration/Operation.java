package com.floatguard.model.integration;

import java.time.Instant;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * A single money movement against a float. Modelled as an interface so that
 * deposits and withdrawals are handled polymorphically (see the concrete
 * {@link CustomerDeposit} and {@link CustomerWithdrawal}).
 *
 * Implementations MUST reject non-positive amounts in their constructor, so the
 * REST layer only needs to guard against missing / non-numeric input.
 */
public interface Operation {

    /** Positive magnitude of the operation (never negative). */
    double getAmount();

    /** {@code true} for a deposit (money into the float), {@code false} for a withdrawal. */
    boolean isDeposit();

    /** When the operation was recorded (used for runway estimation). */
    Instant getTimestamp();

    /**
     * Signed effect of the operation on a float balance: +amount for deposits,
     * -amount for withdrawals.
     */
    default double signedAmount() {
        return isDeposit() ? getAmount() : -getAmount();
    }

    /** Discriminator used for persistence and JSON ("DEPOSIT" / "WITHDRAWAL"). */
    default String getOperationType() {
        return isDeposit() ? "DEPOSIT" : "WITHDRAWAL";
    }
}
