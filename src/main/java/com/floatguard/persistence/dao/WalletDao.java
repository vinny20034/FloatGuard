package com.floatguard.persistence.dao;

import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.WalletType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQL mapper for the {@code wallets} table. Rebuilds the correct
 * {@link FloatWallet} subclass from the persisted {@code type} column, so the
 * domain polymorphism survives a restart.
 */
public class WalletDao {

    public void insert(Connection connection, FloatWallet wallet, Instant createdAt) throws SQLException {
        String sql = "INSERT INTO wallets (id, agent_id, type, balance, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, wallet.getId());
            statement.setString(2, wallet.getAgentId());
            statement.setString(3, wallet.getType().name());
            statement.setDouble(4, wallet.getBalance());
            statement.setString(5, createdAt.toString());
            statement.executeUpdate();
        }
    }

    public void updateBalance(Connection connection, FloatWallet wallet) throws SQLException {
        String sql = "UPDATE wallets SET balance = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, wallet.getBalance());
            statement.setString(2, wallet.getId());
            statement.executeUpdate();
        }
    }

    public Optional<FloatWallet> findById(Connection connection, String walletId) throws SQLException {
        String sql = "SELECT id, agent_id, type, balance FROM wallets WHERE id = ?";
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

    public List<FloatWallet> findByAgentId(Connection connection, String agentId) throws SQLException {
        String sql = "SELECT id, agent_id, type, balance FROM wallets WHERE agent_id = ? ORDER BY type";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, agentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<FloatWallet> wallets = new ArrayList<>();
                while (resultSet.next()) {
                    wallets.add(mapRow(resultSet));
                }
                return wallets;
            }
        }
    }

    public boolean existsByAgentAndType(Connection connection, String agentId, WalletType type) throws SQLException {
        String sql = "SELECT 1 FROM wallets WHERE agent_id = ? AND type = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, agentId);
            statement.setString(2, type.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private FloatWallet mapRow(ResultSet resultSet) throws SQLException {
        return FloatWallet.create(
                WalletType.valueOf(resultSet.getString("type")),
                resultSet.getString("id"),
                resultSet.getString("agent_id"),
                resultSet.getDouble("balance"));
    }
}
