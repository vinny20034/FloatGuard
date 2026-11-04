package com.floatguard.service;

import com.floatguard.error.ConflictException;
import com.floatguard.error.ForbiddenException;
import com.floatguard.error.NotFoundException;
import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.CustomerDeposit;
import com.floatguard.model.integration.CustomerWithdrawal;
import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.InsufficientFloatException;
import com.floatguard.model.integration.Operation;
import com.floatguard.model.integration.RebalanceRequest;
import com.floatguard.model.integration.RebalanceStatus;
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
    public ServiceResults.AgentCreation registerAgent(String name, String location,
                                                      String nrcNumber, String phoneNumber,
                                                      String superAgentId) {
        Instant createdAt = Instant.now();
        Agent agent = new Agent(UUID.randomUUID().toString(), name, location,
                nrcNumber, phoneNumber, Agent.AGENT);
        database.inTransaction(connection -> {
            Agent superAgent = requireSuperAgent(connection, superAgentId);
            requireSameLocation(superAgent, agent);
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

            return new ServiceResults.OperationLogEntry(
                    operationId, wallet, operation, wallet.getBalance(), null);
        });
    }

    /**
     * Records a customer cash-in or cash-out as equal-and-opposite movements
     * across the agent's CASH and EFLOAT wallets.
     */
    public ServiceResults.OperationLogEntry logCustomerTransaction(String cashWalletId, Operation cashOperation) {
        String cashOperationId = UUID.randomUUID().toString();
        String eFloatOperationId = UUID.randomUUID().toString();
        return database.inTransaction(connection -> {
            FloatWallet cashWallet = walletDao.findById(connection, cashWalletId)
                    .orElseThrow(() -> new NotFoundException("Unknown wallet '" + cashWalletId + "'."));
            if (cashWallet.getType() != WalletType.CASH) {
                throw new ConflictException("Customer transactions must use the agent's CASH wallet.");
            }

            FloatWallet eFloatWallet = walletDao.findByAgentId(connection, cashWallet.getAgentId()).stream()
                    .filter(wallet -> wallet.getType() == WalletType.EFLOAT)
                    .findFirst()
                    .orElseThrow(() -> new ConflictException(
                            "Customer transactions require both CASH and EFLOAT wallets."));

            Operation eFloatOperation = cashOperation.isDeposit()
                    ? new CustomerWithdrawal(cashOperation.getAmount(), cashOperation.getTimestamp())
                    : new CustomerDeposit(cashOperation.getAmount(), cashOperation.getTimestamp());
            double eFloatBalanceBefore = eFloatWallet.getBalance();

            // Apply both domain rules before writing either balance or ledger entry.
            cashWallet.applyOperation(cashOperation);
            eFloatWallet.applyOperation(eFloatOperation);

            walletDao.updateBalance(connection, cashWallet);
            walletDao.updateBalance(connection, eFloatWallet);
            operationDao.insert(connection, cashOperationId, cashWalletId, cashOperation, cashWallet.getBalance());
            operationDao.insert(connection, eFloatOperationId, eFloatWallet.getId(),
                    eFloatOperation, eFloatWallet.getBalance());

            return new ServiceResults.OperationLogEntry(
                    cashOperationId,
                    cashWallet,
                    cashOperation,
                    cashWallet.getBalance(),
                    null,
                    new ServiceResults.PairedOperationLogEntry(
                            eFloatOperationId,
                            eFloatWallet,
                            eFloatOperation,
                            eFloatBalanceBefore,
                            eFloatWallet.getBalance()));
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

    /** GET /admin/profile - the signed-in super agent and its wallet balances. */
    public ServiceResults.AgentRunway getSuperAgentProfile(String superAgentId) {
        return database.read(connection -> {
            Agent profile = requireSuperAgent(connection, superAgentId);
            return runwayFor(connection, profile);
        });
    }

    /** GET /admin/agents - only ordinary agents at this super agent's location. */
    public List<ServiceResults.AgentRunway> listAgentRunways(String superAgentId) {
        return database.read(connection -> {
            List<ServiceResults.AgentRunway> results = new ArrayList<>();
            Agent superAgent = requireSuperAgent(connection, superAgentId);
            for (Agent agent : agentDao.findAgentsInLocation(connection, superAgent.getLocation())) {
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
    public ServiceResults.OperationLogEntry assignFloat(String superAgentId, String agentId,
                                                        WalletType type, double amount) {
        return database.inTransaction(connection -> {
            Agent superAgent = requireSuperAgent(connection, superAgentId);
            Agent agent = requireAgentInSuperAgentLocation(connection, agentId, superAgent);
            FloatWallet source = findWallet(connection, superAgentId, type, "Super agent");
            FloatWallet destination = findWallet(connection, agent.getId(), type, "Agent");
            Instant occurredAt = Instant.now();

            source.applyOperation(new CustomerWithdrawal(amount, occurredAt));
            destination.applyOperation(new CustomerDeposit(amount, occurredAt));
            walletDao.updateBalance(connection, source);
            walletDao.updateBalance(connection, destination);
            operationDao.insert(connection, UUID.randomUUID().toString(), source.getId(),
                    new CustomerWithdrawal(amount, occurredAt), source.getBalance());
            CustomerDeposit deposit = new CustomerDeposit(amount, occurredAt);
            operationDao.insert(connection, UUID.randomUUID().toString(), destination.getId(),
                    deposit, destination.getBalance());

            return new ServiceResults.OperationLogEntry(
                    destination.getId(), destination, deposit, destination.getBalance(), null);
        });
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

    /**
     * Approves a pending request and records the requested top-up.
     *
     * <p>A top-up is bought with the opposite float: an EFLOAT top-up is paid for
     * in physical cash and a CASH top-up is paid for in e-float. That amount
     * moves from the agent's wallet to the super agent's wallet of the same type
     * in the same transaction. An agent without enough of the paying float rolls
     * the whole approval back.</p>
     */
    public ServiceResults.RebalanceApproval approveRebalance(String rebalanceId,
                                                             String superAgentUserId,
                                                             String superAgentId) {
        return database.inTransaction(connection -> {
            RebalanceRequest request = rebalanceDao.findById(connection, rebalanceId)
                    .orElseThrow(() -> new NotFoundException("Unknown rebalance request '" + rebalanceId + "'."));
            if (request.getStatus() != RebalanceStatus.PENDING) {
                throw new ConflictException("This rebalance request has already been approved.");
            }
            Agent superAgent = requireSuperAgent(connection, superAgentId);
            if (rebalanceDao.hasPassed(connection, rebalanceId, superAgentUserId)) {
                throw new ConflictException("You have passed on this request; another local super agent can approve it.");
            }
            FloatWallet wallet = walletDao.findById(connection, request.getWalletId())
                    .orElseThrow(() -> new NotFoundException("Unknown wallet '" + request.getWalletId() + "'."));
            Agent requestingAgent = agentDao.findById(connection, wallet.getAgentId())
                    .orElseThrow(() -> new NotFoundException("Unknown requesting agent."));
            requireSameLocation(superAgent, requestingAgent);
            if (requestingAgent.isSuperAgent()) {
                throw new ConflictException("Rebalance requests can only be approved for ordinary agents.");
            }

            FloatWallet source = findWallet(connection, superAgentId, wallet.getType(), "Super agent");
            Instant approvedAt = Instant.now();
            CustomerWithdrawal withdrawal = new CustomerWithdrawal(request.getAmount(), approvedAt);
            CustomerDeposit topUp = new CustomerDeposit(request.getAmount(), approvedAt);

            // A top-up is bought with the other float: e-float is paid for in cash
            // and cash is paid for in e-float. That amount moves from the agent's
            // wallet to the super agent's wallet of the same type.
            WalletType paymentType = wallet.getType().opposite();
            FloatWallet agentPayment = findWallet(connection, requestingAgent.getId(), paymentType, "Agent");
            FloatWallet superAgentPayment = findWallet(connection, superAgentId, paymentType, "Super agent");
            CustomerWithdrawal paymentSent = new CustomerWithdrawal(request.getAmount(), approvedAt);
            CustomerDeposit paymentReceived = new CustomerDeposit(request.getAmount(), approvedAt);

            // Every rule is checked before a single balance or ledger row is written.
            source.applyOperation(withdrawal);
            wallet.applyOperation(topUp);
            agentPayment.applyOperation(paymentSent);
            superAgentPayment.applyOperation(paymentReceived);

            walletDao.updateBalance(connection, source);
            walletDao.updateBalance(connection, wallet);
            operationDao.insert(connection, UUID.randomUUID().toString(), source.getId(),
                    withdrawal, source.getBalance());
            operationDao.insert(connection, UUID.randomUUID().toString(), wallet.getId(),
                    topUp, wallet.getBalance());

            walletDao.updateBalance(connection, agentPayment);
            walletDao.updateBalance(connection, superAgentPayment);
            operationDao.insert(connection, UUID.randomUUID().toString(), agentPayment.getId(),
                    paymentSent, agentPayment.getBalance());
            operationDao.insert(connection, UUID.randomUUID().toString(), superAgentPayment.getId(),
                    paymentReceived, superAgentPayment.getBalance());

            request.complete();
            rebalanceDao.update(connection, request);
            return new ServiceResults.RebalanceApproval(
                    request, wallet.getType(), wallet.getBalance(), source.getBalance());
        });
    }

    /** Lists requests from the super agent's own location, excluding requests they passed. */
    public List<ServiceResults.AdminRebalanceView> listRebalanceRequests(
            String superAgentUserId, String superAgentId) {
        return database.read(connection -> {
            Agent superAgent = requireSuperAgent(connection, superAgentId);
            List<ServiceResults.AdminRebalanceView> results = new ArrayList<>();
            for (RebalanceRequest request :
                    rebalanceDao.listAvailableForSuperAgent(connection, superAgent.getLocation(), superAgentUserId)) {
                FloatWallet wallet = walletDao.findById(connection, request.getWalletId())
                        .orElseThrow(() -> new NotFoundException("Unknown wallet '" + request.getWalletId() + "'."));
                Agent agent = agentDao.findById(connection, wallet.getAgentId())
                        .orElseThrow(() -> new NotFoundException("Unknown agent '" + wallet.getAgentId() + "'."));
                if (agent.isSuperAgent()) {
                    continue;
                }
                results.add(new ServiceResults.AdminRebalanceView(
                        request.getId(),
                        agent.getId(),
                        agent.getName(),
                        agent.getPhoneNumber(),
                        agent.getLocation(),
                        request.getArea(),
                        wallet.getType(),
                        request.getAmount(),
                        request.getStatus().name(),
                        rebalanceDao.countPasses(connection, request.getId()),
                        request.getCreatedAt().toString(),
                        request.getCompletedAt() == null ? null : request.getCompletedAt().toString()));
            }
            return results;
        });
    }

    /** Passes a request for another super agent in the same location to handle. */
    public void passRebalance(String rebalanceId, String superAgentUserId, String superAgentId) {
        database.inTransaction(connection -> {
            RebalanceRequest request = rebalanceDao.findById(connection, rebalanceId)
                    .orElseThrow(() -> new NotFoundException("Unknown rebalance request '" + rebalanceId + "'."));
            if (request.getStatus() != RebalanceStatus.PENDING) {
                throw new ConflictException("Only a pending request can be passed to another super agent.");
            }
            Agent superAgent = requireSuperAgent(connection, superAgentId);
            FloatWallet wallet = walletDao.findById(connection, request.getWalletId())
                    .orElseThrow(() -> new NotFoundException("Unknown wallet '" + request.getWalletId() + "'."));
            Agent requestingAgent = agentDao.findById(connection, wallet.getAgentId())
                    .orElseThrow(() -> new NotFoundException("Unknown requesting agent."));
            requireSameLocation(superAgent, requestingAgent);
            if (rebalanceDao.hasPassed(connection, rebalanceId, superAgentUserId)) {
                throw new ConflictException("You have already passed on this request.");
            }
            rebalanceDao.recordPass(connection, rebalanceId, superAgentUserId, Instant.now());
            return null;
        });
    }

    /**
     * Deletes a rebalance request in the super agent's location. Lets an
     * administrator clear a request that can no longer be approved (for example
     * one that has been passed around), freeing the wallet for a new request.
     */
    public void cancelRebalance(String rebalanceId, String superAgentId) {
        database.inTransaction(connection -> {
            RebalanceRequest request = rebalanceDao.findById(connection, rebalanceId)
                    .orElseThrow(() -> new NotFoundException("Unknown rebalance request '" + rebalanceId + "'."));
            Agent superAgent = requireSuperAgent(connection, superAgentId);
            FloatWallet wallet = walletDao.findById(connection, request.getWalletId())
                    .orElseThrow(() -> new NotFoundException("Unknown wallet '" + request.getWalletId() + "'."));
            Agent requestingAgent = agentDao.findById(connection, wallet.getAgentId())
                    .orElseThrow(() -> new NotFoundException("Unknown requesting agent."));
            requireSameLocation(superAgent, requestingAgent);
            rebalanceDao.delete(connection, rebalanceId);
            return null;
        });
    }

    /**
     * Cancels one of the agent's own pending requests so the wallet is freed for
     * a new one. Only a wallet the agent owns can be cancelled, and only while
     * the request is still pending.
     */
    public void cancelOwnRebalance(String agentId, String rebalanceId) {
        database.inTransaction(connection -> {
            RebalanceRequest request = rebalanceDao.findById(connection, rebalanceId)
                    .orElseThrow(() -> new NotFoundException("Unknown rebalance request '" + rebalanceId + "'."));
            FloatWallet wallet = walletDao.findById(connection, request.getWalletId())
                    .orElseThrow(() -> new NotFoundException("Unknown wallet '" + request.getWalletId() + "'."));
            if (!wallet.getAgentId().equals(agentId)) {
                throw new ForbiddenException("That rebalance request does not belong to this agent.");
            }
            if (request.getStatus() != RebalanceStatus.PENDING) {
                throw new ConflictException("Only a pending request can be cancelled.");
            }
            rebalanceDao.delete(connection, rebalanceId);
            return null;
        });
    }

    /**
     * GET /agents/{id}/rebalances - the agent's floats measured against the
     * threshold, plus every request submitted for its wallets.
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
                requests.add(toView(connection, request, typeByWalletId.get(request.getWalletId())));
            }

            return new ServiceResults.RebalanceOverview(
                    agentId, FloatWallet.REBALANCE_THRESHOLD, walletViews, requests);
        });
    }

    /**
     * POST /agents/{id}/rebalances - submits an amount, float type and the
     * agent's declared proximate area. A wallet cannot have multiple outstanding
     * requests.
     */
    public ServiceResults.RebalanceView requestRebalance(String agentId, WalletType type,
                                                         double amount, String area) {
        return database.inTransaction(connection -> {
            FloatWallet wallet = walletDao.findByAgentId(connection, agentId).stream()
                    .filter(candidate -> candidate.getType() == type)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Agent '" + agentId + "' has no " + type + " wallet."));

            if (rebalanceDao.findPendingByWalletId(connection, wallet.getId()).isPresent()) {
                throw new ConflictException("This wallet already has a pending rebalance request.");
            }

            // A top-up is paid for with the opposite float, so the agent must
            // already hold enough of it. Rejecting here stops requests that could
            // never be approved and would otherwise leave the agent locked out.
            WalletType paymentType = wallet.getType().opposite();
            FloatWallet paymentWallet = findWallet(connection, agentId, paymentType, "Agent");
            if (paymentWallet.getBalance() < amount) {
                throw new InsufficientFloatException("Your " + paymentType
                        + " balance is too low to pay for this " + type + " top-up.");
            }

            RebalanceRequest pending = wallet.createRebalanceRequest(amount, area);
            rebalanceDao.insert(connection, pending);
            return toView(connection, pending, type);
        });
    }

    private ServiceResults.RebalanceView toView(Connection connection, RebalanceRequest request, WalletType type)
            throws SQLException {
        return new ServiceResults.RebalanceView(
                request.getId(),
                type,
                request.getAmount(),
                request.getArea(),
                request.getStatus().name(),
                rebalanceDao.countPasses(connection, request.getId()),
                request.getCreatedAt().toString(),
                request.getCompletedAt() == null ? null : request.getCompletedAt().toString());
    }

    private Agent requireSuperAgent(Connection connection, String superAgentId) throws SQLException {
        if (superAgentId == null) {
            throw new ConflictException("This administrator account has no super-agent profile. Register a super-agent account.");
        }
        Agent profile = agentDao.findById(connection, superAgentId)
                .orElseThrow(() -> new NotFoundException("Unknown super-agent profile."));
        if (!profile.isSuperAgent()) {
            throw new ConflictException("This account is not linked to a super-agent profile.");
        }
        return profile;
    }

    private Agent requireAgentInSuperAgentLocation(Connection connection, String agentId, Agent superAgent)
            throws SQLException {
        Agent agent = agentDao.findById(connection, agentId)
                .orElseThrow(() -> new NotFoundException("Unknown agent '" + agentId + "'."));
        requireSameLocation(superAgent, agent);
        if (agent.isSuperAgent()) {
            throw new ConflictException("Float can only be assigned to an ordinary agent.");
        }
        return agent;
    }

    private void requireSameLocation(Agent superAgent, Agent agent) {
        if (!superAgent.getLocation().trim().equalsIgnoreCase(agent.getLocation().trim())) {
            throw new com.floatguard.error.ForbiddenException(
                    "You can only manage rebalance requests and agents in your registered location.");
        }
    }

    private FloatWallet findWallet(Connection connection, String ownerId, WalletType type, String ownerLabel)
            throws SQLException {
        return walletDao.findByAgentId(connection, ownerId).stream()
                .filter(wallet -> wallet.getType() == type)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ownerLabel + " has no " + type + " wallet."));
    }
}
