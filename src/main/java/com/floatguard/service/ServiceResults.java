package com.floatguard.service;

import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.Operation;
import com.floatguard.model.integration.RunwayEstimate;

import java.time.Instant;
import java.util.List;

/**
 * Return types for {@link FloatGuardService}. Grouped here so the service
 * package stays compact; each nested record is a plain data carrier with no
 * behaviour of its own.
 */
public final class ServiceResults {

    private ServiceResults() {
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
}
