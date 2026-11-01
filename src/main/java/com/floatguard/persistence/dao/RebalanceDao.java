package com.floatguard.persistence.dao;

import com.floatguard.model.integration.RebalanceRequest;
import com.floatguard.model.integration.RebalanceStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * SQL mapper for the {@code rebalance_requests} table.
 */
public class RebalanceDao {

    public void insert(Connection connection, RebalanceRequest request) throws SQLException {
        String sql = "INSERT INTO rebalance_requests (id, wallet_id, status, created_at, completed_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.getId());
            statement.setString(2, request.getWalletId());
            statement.setString(3, request.getStatus().name());
            statement.setString(4, request.getCreatedAt().toString());
            statement.setString(5, request.getCompletedAt() == null ? null : request.getCompletedAt().toString());
            statement.executeUpdate();
        }
    }

    public void update(Connection connection, RebalanceRequest request) throws SQLException {
        String sql = "UPDATE rebalance_requests SET status = ?, completed_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.getStatus().name());
            statement.setString(2, request.getCompletedAt() == null ? null : request.getCompletedAt().toString());
            statement.setString(3, request.getId());
            statement.executeUpdate();
        }
    }

    public Optional<RebalanceRequest> findById(Connection connection, String rebalanceId) throws SQLException {
        String sql = "SELECT id, wallet_id, status, created_at, completed_at FROM rebalance_requests WHERE id = ?";
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

    /** Used to avoid raising duplicate rebalances for the same wallet. */
    public Optional<RebalanceRequest> findPendingByWalletId(Connection connection, String walletId) throws SQLException {
        String sql = "SELECT id, wallet_id, status, created_at, completed_at FROM rebalance_requests "
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
                RebalanceStatus.valueOf(resultSet.getString("status")),
                Instant.parse(resultSet.getString("created_at")),
                completedAtRaw == null ? null : Instant.parse(completedAtRaw));
    }
}
