package com.floatguard.model.integration;

import java.time.Instant;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * A request to top up a wallet. Created PENDING (e.g. when a float drops below
 * the rebalance threshold) and later marked COMPLETED via {@code PUT /rebalance/{id}}.
 */
public class RebalanceRequest {

    private final String id;
    private final String walletId;
    private final Instant createdAt;
    private RebalanceStatus status;
    private Instant completedAt;

    /** Used when raising a brand-new request. */
    public RebalanceRequest(String id, String walletId, RebalanceStatus status, Instant createdAt) {
        this(id, walletId, status, createdAt, null);
    }

    /** Full constructor - used when rehydrating an existing request from storage. */
    public RebalanceRequest(String id, String walletId, RebalanceStatus status, Instant createdAt, Instant completedAt) {
        this.id = id;
        this.walletId = walletId;
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
