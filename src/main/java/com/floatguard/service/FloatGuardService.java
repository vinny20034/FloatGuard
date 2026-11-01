package com.floatguard.service;

import com.floatguard.error.ConflictException;
import com.floatguard.error.NotFoundException;
import com.floatguard.model.integration.Agent;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

            List<RunwayEstimate> estimates = new ArrayList<>();
            for (FloatWallet wallet : walletDao.findByAgentId(connection, agentId)) {
                List<Operation> history = operationDao.findByWalletId(connection, wallet.getId());
                // DOMAIN CALL: estimation + warning live in RunwayEstimator.
                estimates.add(RunwayEstimator.estimate(wallet, history));
            }
            return new ServiceResults.AgentRunway(agent, estimates, RunwayEstimator.overallWarning(estimates));
        });
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
}
