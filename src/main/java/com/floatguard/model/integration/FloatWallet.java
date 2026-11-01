package com.floatguard.model.integration;

import java.util.UUID;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * Base class for the two floats an agent holds. Owns the single hard rule of the
 * domain - a float may never go negative - and the low-float threshold that
 * decides when a rebalance should be raised.
 *
 * <p>The REST layer never touches {@code balance} directly: it always calls
 * {@link #applyOperation(Operation)} so the non-negative invariant cannot be
 * bypassed by the transport layer.</p>
 */
public abstract class FloatWallet {

    /**
     * DOMAIN RULE: when a wallet drops below this amount the agent is considered
     * to need a rebalance. Placeholder value - the domain owner should replace it
     * with the agreed threshold (or make it configurable per agent).
     */
    public static final double REBALANCE_THRESHOLD = 200.0;

    private final String id;
    private final String agentId;
    private double balance;

    protected FloatWallet(String id, String agentId, double balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative.");
        }
        this.id = id;
        this.agentId = agentId;
        this.balance = balance;
    }

    /** Convenience factory that maps a persisted/requested type to the right subclass. */
    public static FloatWallet create(WalletType type, String id, String agentId, double balance) {
        return switch (type) {
            case CASH -> new CashFloat(id, agentId, balance);
            case EFLOAT -> new EFloat(id, agentId, balance);
        };
    }

    public abstract WalletType getType();

    public String getId() {
        return id;
    }

    public String getAgentId() {
        return agentId;
    }

    public double getBalance() {
        return balance;
    }

    /**
     * Applies an operation to this float, preserving the non-negative invariant.
     *
     * @throws InsufficientFloatException when the resulting balance would be below zero
     */
    public void applyOperation(Operation operation) {
        double updatedBalance = balance + operation.signedAmount();
        if (updatedBalance < 0) {
            throw new InsufficientFloatException(
                    getType() + " float balance " + balance + " is insufficient for a "
                            + operation.getOperationType() + " of " + operation.getAmount());
        }
        this.balance = updatedBalance;
    }

    /** True when this wallet has fallen below {@link #REBALANCE_THRESHOLD}. */
    public boolean isBelowRebalanceThreshold() {
        return balance < REBALANCE_THRESHOLD;
    }

    /** Builds a fresh PENDING rebalance request targeting this wallet. */
    public RebalanceRequest createRebalanceRequest() {
        return new RebalanceRequest(UUID.randomUUID().toString(), id, RebalanceStatus.PENDING, java.time.Instant.now());
    }
}
