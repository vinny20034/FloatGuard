package com.floatguard.service;

import com.floatguard.model.auth.User;
import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.Operation;
import com.floatguard.model.integration.RebalanceRequest;
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

    /** The outcome of logging a deposit or withdrawal. */
    public record OperationLogEntry(String id,
                                    FloatWallet wallet,
                                    Operation operation,
                                    double balanceAfter,
                                    String rebalanceRequestId,
                                    PairedOperationLogEntry pairedOperation) {
        public OperationLogEntry(String id,
                                 FloatWallet wallet,
                                 Operation operation,
                                 double balanceAfter,
                                 String rebalanceRequestId) {
            this(id, wallet, operation, balanceAfter, rebalanceRequestId, null);
        }
    }

    /** The matching wallet movement made as part of a customer transaction. */
    public record PairedOperationLogEntry(String id,
                                          FloatWallet wallet,
                                          Operation operation,
                                          double balanceBefore,
                                          double balanceAfter) {
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
                                double amount,
                                String area,
                                String status,
                                int handoffCount,
                                String createdAt,
                                String completedAt) {
    }

    /** Everything the agent rebalance page needs: floats, threshold and requests. */
    public record RebalanceOverview(String agentId,
                                    double threshold,
                                    List<RebalanceWallet> wallets,
                                    List<RebalanceView> requests) {
    }

    /** A request enriched with the agent identity for the administrator queue. */
    public record AdminRebalanceView(String id,
                                     String agentId,
                                     String agentName,
                                     String agentPhoneNumber,
                                     String location,
                                     String area,
                                     WalletType type,
                                     double amount,
                                     String status,
                                     int handoffCount,
                                     String createdAt,
                                     String completedAt) {
    }

    /** A confirmed top-up and the resulting wallet balance. */
    public record RebalanceApproval(RebalanceRequest request, WalletType type,
                                    double balanceAfter, double sourceBalanceAfter) {
    }
}
