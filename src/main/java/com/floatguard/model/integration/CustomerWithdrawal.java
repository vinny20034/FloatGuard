package com.floatguard.model.integration;

import java.time.Instant;
import java.util.Objects;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * A customer taking money out of the agent's float: the float balance decreases.
 */
public final class CustomerWithdrawal implements Operation {

    private final double amount;
    private final Instant timestamp;

    public CustomerWithdrawal(double amount, Instant timestamp) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }
        this.amount = amount;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    @Override
    public double getAmount() {
        return amount;
    }

    @Override
    public boolean isDeposit() {
        return false;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
