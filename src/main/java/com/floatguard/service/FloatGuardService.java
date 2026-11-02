package com.floatguard.service;

import com.floatguard.error.ConflictException;
import com.floatguard.error.NotFoundException;
import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.CustomerDeposit;
import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.Operation;
import com.floatguard.model.integration.RebalanceRequest;
import com.floatguard.model.integration.RunwayEstimate;
import com.floatguard.model.integration.RunwayEstimator;
import com.floatguard.model.integration.WalletType;
import com.floatguard.persistence.Database;
import com.floatguard.persistence.dao.AgentDao;
import com.floatguard.persistence.dao.OperationDao;
import com.floatguard.persistence.dao.RebalanceDao;
import com.floatguard.persistence.dao.WalletDao;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: the ONLY place that coordinates persistence with the
 * domain objects. It contains no HTTP knowledge and no business rules - it loads
 * aggregates, delegates the actual decisions to the domain (FloatWallet,
 * RunwayEstimator, RebalanceRequest) and writes the results back atomically.
 *
 * <p>The API layer depends on this class, never on the DAOs directly.</p>
 */
public class FloatGuardService {

    private final Database database;
    private final AgentDao agentDao;
    private final WalletDao walletDao;
    private final OperationDao operationDao;
    private final RebalanceDao rebalanceDao;

    public FloatGuardService(Database database,
                             AgentDao agentDao,
                             WalletDao walletDao,
                             OperationDao operationDao,
                             RebalanceDao rebalanceDao) {
        this.database = database;
        this.agentDao = agentDao;
        this.walletDao = walletDao;
        this.operationDao = operationDao;
        this.rebalanceDao = rebalanceDao;
    }

    /** POST /agents - registers a new agent. */
    public ServiceResults.AgentCreation registerAgent(String name, String location) {
        Instant createdAt = Instant.now();
        Agent agent = new Agent(UUID.randomUUID().toString(), name, location);
        database.inTransaction(connection -> {
            agentDao.insert(connection, agent, createdAt);
            return null;
        });
        return new ServiceResults.AgentCreation(agent, createdAt);
    }

    /** POST /agents/{id}/wallets - creates one wallet (CASH or EFLOAT) for an agent. */
    public ServiceResults.WalletCreation createWallet(String agentId, WalletType type, double openingBalance) {
        Instant createdAt = Instant.now();
        FloatWallet wallet = database.inTransaction(connection -> {
            if (!agentDao.existsById(connection, agentId)) {
                throw new NotFoundException("Unknown agent '" + agentId + "'.");
            }
            // "one wallet per type" is enforced both here (clear error) and by the
            // UNIQUE(agent_id, type) constraint in the schema (safety net).
            if (walletDao.existsByAgentAndType(connection, agentId, type)) {
                throw new ConflictException("Agent '" + agentId + "' already has a " + type + " wallet.");
            }
            FloatWallet created = FloatWallet.create(type, UUID.randomUUID().toString(), agentId, openingBalance);
            walletDao.insert(connection, created, createdAt);
            return created;
        });
        return new ServiceResults.WalletCreation(wallet, createdAt);
    }

    /**
     * POST /deposit and POST /withdraw - logs an operation against a wallet.
     *
     * <p>Everything happens in one transaction so the wallet balance and its
     * ledger row can never drift apart. The domain's
     * {@link com.floatguard.model.integration.InsufficientFloatException} is
     * allowed to bubble up untouched and is mapped to 409 by the API layer.</p>
     */
    public ServiceResults.OperationLogEntry logOperation(String walletId, Operation operation) {
        String operationId = UUID.randomUUID().toString();
        return database.inTransaction(connection -> {
            FloatWallet wallet = walletDao.findById(connection, walletId)
                    .orElseThrow(() -> new NotFoundException("Unknown wallet '" + walletId + "'."));

            // DOMAIN CALL: applies the balance change and enforces the non-negative rule.
            wallet.applyOperation(operation);

            walletDao.updateBalance(connection, wallet);
            operationDao.insert(connection, operationId, walletId, operation, wallet.getBalance());

            // When the float has dropped below the domain threshold, make sure exactly
            // one PENDING rebalance request exists for the wallet.
            String rebalanceRequestId = null;
            if (wallet.isBelowRebalanceThreshold()) {
                RebalanceRequest pending = rebalanceDao.findPendingByWalletId(connection, walletId).orElse(null);
                if (pending == null) {
                    pending = wallet.createRebalanceRequest();
                    rebalanceDao.insert(connection, pending);
                }
                rebalanceRequestId = pending.getId();
            }
            return new ServiceResults.OperationLogEntry(
                    operationId, wallet, operation, wallet.getBalance(), rebalanceRequestId);
        });
    }

    /** GET /agents/{id}/runway - balances + runway estimates + warning. */
    public ServiceResults.AgentRunway getRunway(String agentId) {
        return database.read(connection -> {
            Agent agent = agentDao.findById(connection, agentId)
                    .orElseThrow(() -> new NotFoundException("Unknown agent '" + agentId + "'."));
            return runwayFor(connection, agent);
        });
    }

    /** GET /admin/agents - every agent with its wallets, balances and warnings. */
    public List<ServiceResults.AgentRunway> listAgentRunways() {
        return database.read(connection -> {
            List<ServiceResults.AgentRunway> results = new ArrayList<>();
            for (Agent agent : agentDao.findAll(connection)) {
                results.add(runwayFor(connection, agent));
            }
            return results;
        });
    }

    /**
     * POST /admin/agents/{id}/assign-float - an administrator tops up one of an
     * agent's wallets. Recorded as a deposit so the change is auditable and the
     * runway estimate reflects the top-up.
     */
    public ServiceResults.OperationLogEntry assignFloat(String agentId, WalletType type, double amount) {
        String walletId = database.read(connection -> {
            if (!agentDao.existsById(connection, agentId)) {
                throw new NotFoundException("Unknown agent '" + agentId + "'.");
            }
            return walletDao.findByAgentId(connection, agentId).stream()
                    .filter(wallet -> wallet.getType() == type)
                    .map(FloatWallet::getId)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Agent '" + agentId + "' has no " + type + " wallet."));
        });
        return logOperation(walletId, new CustomerDeposit(amount, Instant.now()));
    }

    /**
     * The agent that owns a wallet, or empty when the wallet does not exist.
     * Used by the API layer to authorise wallet-scoped operations.
     */
    public Optional<String> findWalletOwner(String walletId) {
        return database.read(connection -> walletDao.findById(connection, walletId).map(FloatWallet::getAgentId));
    }

    private ServiceResults.AgentRunway runwayFor(Connection connection, Agent agent) throws SQLException {
        List<RunwayEstimate> estimates = new ArrayList<>();
        for (FloatWallet wallet : walletDao.findByAgentId(connection, agent.getId())) {
            List<Operation> history = operationDao.findByWalletId(connection, wallet.getId());
            // DOMAIN CALL: estimation + warning live in RunwayEstimator.
            estimates.add(RunwayEstimator.estimate(wallet, history));
        }
        return new ServiceResults.AgentRunway(agent, estimates, RunwayEstimator.overallWarning(estimates));
    }

    /** PUT /rebalance/{id} - marks a rebalance request COMPLETED. */
    public RebalanceRequest completeRebalance(String rebalanceId) {
        return database.inTransaction(connection -> {
            RebalanceRequest request = rebalanceDao.findById(connection, rebalanceId)
                    .orElseThrow(() -> new NotFoundException("Unknown rebalance request '" + rebalanceId + "'."));
            // DOMAIN CALL: state transition + completion timestamp.
            request.complete();
            rebalanceDao.update(connection, request);
            return request;
        });
    }

    /**
     * GET /agents/{id}/rebalances - the agent's floats measured against the
     * threshold, plus every request raised on its wallets.
     */
    public ServiceResults.RebalanceOverview getRebalanceOverview(String agentId) {
        return database.read(connection -> {
            if (!agentDao.existsById(connection, agentId)) {
                throw new NotFoundException("Unknown agent '" + agentId + "'.");
            }

            List<ServiceResults.RebalanceWallet> walletViews = new ArrayList<>();
            Map<String, WalletType> typeByWalletId = new HashMap<>();
            for (FloatWallet wallet : walletDao.findByAgentId(connection, agentId)) {
                typeByWalletId.put(wallet.getId(), wallet.getType());
                walletViews.add(new ServiceResults.RebalanceWallet(
                        wallet.getType(), wallet.getBalance(), wallet.isBelowRebalanceThreshold()));
            }

            List<ServiceResults.RebalanceView> requests = new ArrayList<>();
            for (RebalanceRequest request : rebalanceDao.listByAgentId(connection, agentId)) {
                requests.add(toView(request, typeByWalletId.get(request.getWalletId())));
            }

            return new ServiceResults.RebalanceOverview(
                    agentId, FloatWallet.REBALANCE_THRESHOLD, walletViews, requests);
        });
    }

    /**
     * POST /agents/{id}/rebalances - raises a PENDING request for one float. A
     * request is only raised when the float has none pending, so a double click
     * cannot create duplicates.
     */
    public ServiceResults.RebalanceView requestRebalance(String agentId, WalletType type) {
        return database.inTransaction(connection -> {
            FloatWallet wallet = walletDao.findByAgentId(connection, agentId).stream()
                    .filter(candidate -> candidate.getType() == type)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Agent '" + agentId + "' has no " + type + " wallet."));

            RebalanceRequest pending = rebalanceDao.findPendingByWalletId(connection, wallet.getId()).orElse(null);
            if (pending == null) {
                // DOMAIN CALL: the wallet decides how a request for it is built.
                pending = wallet.createRebalanceRequest();
                rebalanceDao.insert(connection, pending);
            }
            return toView(pending, type);
        });
    }

    private ServiceResults.RebalanceView toView(RebalanceRequest request, WalletType type) {
        return new ServiceResults.RebalanceView(
                request.getId(),
                type,
                request.getStatus().name(),
                request.getCreatedAt().toString(),
                request.getCompletedAt() == null ? null : request.getCompletedAt().toString());
    }
}
