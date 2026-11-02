package com.floatguard.service;

import com.floatguard.model.auth.User;
import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.Operation;
import com.floatguard.model.integration.RunwayEstimate;
import com.floatguard.model.integration.WalletType;

import java.time.Instant;
import java.util.List;

/**
 * Return types for the service layer. Grouped here so the service package stays
 * compact; each nested record is a plain data carrier with no behaviour of its
 * own.
 */
public final class ServiceResults {

    private ServiceResults() {
    }

    /** A signed-in (or freshly registered) user and the token for their session. */
    public record Authenticated(User user, String token) {
    }

    /** A newly registered agent plus the timestamp it was persisted with. */
    public record AgentCreation(Agent agent, Instant createdAt) {
    }

    /** A newly created wallet plus the timestamp it was persisted with. */
    public record WalletCreation(FloatWallet wallet, Instant createdAt) {
    }

    /**
     * The outcome of logging a deposit/withdrawal.
     *
     * @param rebalanceRequestId id of the PENDING rebalance request for this wallet
     *                           after the operation, or {@code null} if the float is
     *                           still healthy. Surfaced so the client can call
     *                           {@code PUT /rebalance/{id}} (there is no endpoint that
     *                           lists rebalance requests).
     */
    public record OperationLogEntry(String id,
                                    FloatWallet wallet,
                                    Operation operation,
                                    double balanceAfter,
                                    String rebalanceRequestId) {
    }

    /** An agent's wallets with their runway estimates and an aggregated warning. */
    public record AgentRunway(Agent agent, List<RunwayEstimate> estimates, String overallWarning) {
    }

    /** One float's position measured against the domain rebalance threshold. */
    public record RebalanceWallet(WalletType type, double balance, boolean belowThreshold) {
    }

    /** A single rebalance request, already resolved to the wallet type it targets. */
    public record RebalanceView(String id,
                                WalletType type,
                                String status,
                                String createdAt,
                                String completedAt) {
    }

    /** Everything the agent rebalance page needs: floats, threshold and requests. */
    public record RebalanceOverview(String agentId,
                                    double threshold,
                                    List<RebalanceWallet> wallets,
                                    List<RebalanceView> requests) {
    }
}
