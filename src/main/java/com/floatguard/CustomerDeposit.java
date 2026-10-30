package com.floatguard;

import com.floatguard.exception.InsufficientFloatException;

import java.time.Instant;

public class CustomerDeposit implements Operation {

    private final String provider;
    private final double amount;
    private final Instant timestamp;

    public CustomerDeposit(String provider, double amount, Instant timestamp) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        this.provider = provider.trim();
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public CustomerDeposit(String provider, double amount) {
        this(provider, amount, Instant.now());
    }

    @Override
    public void applyTo(Agent agent) throws InsufficientFloatException {
        CashFloat cash = agent.findCash();
        EFloat eFloat = agent.findEFloat(provider); 
        cash.decrease(amount);                      
        eFloat.increase(amount);
    }

    @Override
    public String getType() {
        return "DEPOSIT";
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public double getAmount() {
        return amount;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public double cashChange() {
        return -amount;
    }

    @Override
    public double eFloatChange() {
        return amount;
    }
}
