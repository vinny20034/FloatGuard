package com.floatguard.persistence.dao;

import com.floatguard.model.integration.RebalanceRequest;
import com.floatguard.model.integration.RebalanceStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQL mapper for the {@code rebalance_requests} table.
 */
public class RebalanceDao {

    public void insert(Connection connection, RebalanceRequest request) throws SQLException {
        String sql = "INSERT INTO rebalance_requests "
                + "(id, wallet_id, requested_amount, area, status, created_at, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.getId());
            statement.setString(2, request.getWalletId());
            statement.setDouble(3, request.getAmount());
            statement.setString(4, request.getArea());
            statement.setString(5, request.getStatus().name());
            statement.setString(6, request.getCreatedAt().toString());
            statement.setString(7, request.getCompletedAt() == null ? null : request.getCompletedAt().toString());
            statement.executeUpdate();
        }
    }

    public void update(Connection connection, RebalanceRequest request) throws SQLException {
        String sql = "UPDATE rebalance_requests "
                + "SET requested_amount = ?, status = ?, completed_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, request.getAmount());
            statement.setString(2, request.getStatus().name());
            statement.setString(3, request.getCompletedAt() == null ? null : request.getCompletedAt().toString());
            statement.setString(4, request.getId());
            statement.executeUpdate();
        }
    }

    /** Removes a request and any handoff records recorded against it. */
    public void delete(Connection connection, String rebalanceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM rebalance_request_passes WHERE request_id = ?")) {
            statement.setString(1, rebalanceId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM rebalance_requests WHERE id = ?")) {
            statement.setString(1, rebalanceId);
            statement.executeUpdate();
        }
    }

    public Optional<RebalanceRequest> findById(Connection connection, String rebalanceId) throws SQLException {
        String sql = "SELECT id, wallet_id, requested_amount, area, status, created_at, completed_at "
                + "FROM rebalance_requests WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rebalanceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    /**
     * Every request raised against any wallet owned by the agent, newest first.
     * The table has no agent column, so the ownership is resolved through a join
     * on {@code wallets}.
     */
    public List<RebalanceRequest> listByAgentId(Connection connection, String agentId) throws SQLException {
        String sql = "SELECT r.id, r.wallet_id, r.requested_amount, r.area, r.status, r.created_at, r.completed_at "
                + "FROM rebalance_requests r JOIN wallets w ON w.id = r.wallet_id "
                + "WHERE w.agent_id = ? ORDER BY r.created_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, agentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RebalanceRequest> requests = new ArrayList<>();
                while (resultSet.next()) {
                    requests.add(mapRow(resultSet));
                }
                return requests;
            }
        }
    }

    public List<RebalanceRequest> listAll(Connection connection) throws SQLException {
        String sql = "SELECT id, wallet_id, requested_amount, area, status, created_at, completed_at "
                + "FROM rebalance_requests ORDER BY created_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<RebalanceRequest> requests = new ArrayList<>();
            return requests;
        }
    }

    public List<RebalanceRequest> listAvailableForSuperAgent(
            Connection connection, String location, String superAgentUserId) throws SQLException {
        String sql = """
                SELECT r.id, r.wallet_id, r.requested_amount, r.area, r.status, r.created_at, r.completed_at
                FROM rebalance_requests r
                JOIN wallets w ON w.id = r.wallet_id
                JOIN agents a ON a.id = w.agent_id
                WHERE LOWER(TRIM(a.location)) = LOWER(TRIM(?))
                  AND (r.status <> 'PENDING' OR NOT EXISTS (
                      SELECT 1 FROM rebalance_request_passes p
                      WHERE p.request_id = r.id AND p.super_agent_user_id = ?
                  ))
                ORDER BY r.created_at DESC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location);
            statement.setString(2, superAgentUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RebalanceRequest> requests = new ArrayList<>();
                while (resultSet.next()) {
                    requests.add(mapRow(resultSet));
                }
                return requests;
            }
        }
    }

    public boolean hasPassed(Connection connection, String requestId, String superAgentUserId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM rebalance_request_passes WHERE request_id = ? AND super_agent_user_id = ?")) {
            statement.setString(1, requestId);
            statement.setString(2, superAgentUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public void recordPass(Connection connection, String requestId, String superAgentUserId, Instant passedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rebalance_request_passes (request_id, super_agent_user_id, passed_at) VALUES (?, ?, ?)")) {
            statement.setString(1, requestId);
            statement.setString(2, superAgentUserId);
            statement.setString(3, passedAt.toString());
            statement.executeUpdate();
        }
    }

    public int countPasses(Connection connection, String requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM rebalance_request_passes WHERE request_id = ?")) {
            statement.setString(1, requestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /** Used to avoid raising duplicate rebalances for the same wallet. */
    public Optional<RebalanceRequest> findPendingByWalletId(Connection connection, String walletId) throws SQLException {
        String sql = "SELECT id, wallet_id, requested_amount, area, status, created_at, completed_at "
                + "FROM rebalance_requests "
                + "WHERE wallet_id = ? AND status = 'PENDING' LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, walletId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    private RebalanceRequest mapRow(ResultSet resultSet) throws SQLException {
        String completedAtRaw = resultSet.getString("completed_at");
        return new RebalanceRequest(
                resultSet.getString("id"),
                resultSet.getString("wallet_id"),
                resultSet.getDouble("requested_amount"),
                resultSet.getString("area"),
                RebalanceStatus.valueOf(resultSet.getString("status")),
                Instant.parse(resultSet.getString("created_at")),
                completedAtRaw == null ? null : Instant.parse(completedAtRaw));
    }
}
