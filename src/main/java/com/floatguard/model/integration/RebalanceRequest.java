package com.floatguard.model.integration;

import java.time.Instant;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * An agent's amount-specific request to top up a wallet. Created PENDING and
 * completed by an administrator's approval.
 */
public class RebalanceRequest {

    private final String id;
    private final String walletId;
    private final double amount;
    private final String area;
    private final Instant createdAt;
    private RebalanceStatus status;
    private Instant completedAt;

    /** Used when raising a brand-new request. */
    public RebalanceRequest(String id, String walletId, double amount, String area,
                            RebalanceStatus status, Instant createdAt) {
        this(id, walletId, amount, area, status, createdAt, null);
    }

    /** Full constructor - used when rehydrating an existing request from storage. */
    public RebalanceRequest(String id, String walletId, double amount, String area,
                            RebalanceStatus status, Instant createdAt, Instant completedAt) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Rebalance amount must be greater than zero.");
        }
        this.id = id;
        this.walletId = walletId;
        this.amount = amount;
        this.area = area == null ? "" : area;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    /**
     * DOMAIN RULE: completes the request. Idempotent - completing an already
     * completed request leaves the original completion timestamp untouched.
     */
    public void complete() {
        if (status != RebalanceStatus.COMPLETED) {
            this.status = RebalanceStatus.COMPLETED;
            this.completedAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getWalletId() {
        return walletId;
    }

    public double getAmount() {
        return amount;
    }

    /** The agent-declared proximate area/town the request was raised from. */
    public String getArea() {
        return area;
    }

    public RebalanceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
