package com.floatguard.model.integration;

import java.time.Instant;
import java.util.Objects;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * A customer handing cash / e-money to the agent: the float balance increases.
 */
public final class CustomerDeposit implements Operation {

    private final double amount;
    private final Instant timestamp;

    public CustomerDeposit(double amount, Instant timestamp) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
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
        return true;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
