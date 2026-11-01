package com.floatguard.persistence.dao;

import com.floatguard.model.integration.CustomerDeposit;
import com.floatguard.model.integration.CustomerWithdrawal;
import com.floatguard.model.integration.Operation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL mapper for the {@code operations} table. This is the append-only ledger of
 * every deposit and withdrawal; it also feeds runway estimation after a restart.
 */
public class OperationDao {

    public void insert(Connection connection, String operationId, String walletId,
                       Operation operation, double balanceAfter) throws SQLException {
        String sql = "INSERT INTO operations (id, wallet_id, type, amount, balance_after, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId);
            statement.setString(2, walletId);
            statement.setString(3, operation.getOperationType());
            statement.setDouble(4, operation.getAmount());
            statement.setDouble(5, balanceAfter);
            statement.setString(6, operation.getTimestamp().toString());
            statement.executeUpdate();
        }
    }

    /**
     * Rebuilds the polymorphic {@link Operation} objects for a wallet. The stored
     * {@code type} decides which concrete class is instantiated.
     */
    public List<Operation> findByWalletId(Connection connection, String walletId) throws SQLException {
        String sql = "SELECT type, amount, occurred_at FROM operations WHERE wallet_id = ? ORDER BY occurred_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, walletId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Operation> operations = new ArrayList<>();
                while (resultSet.next()) {
                    double amount = resultSet.getDouble("amount");
                    Instant occurredAt = Instant.parse(resultSet.getString("occurred_at"));
                    boolean deposit = "DEPOSIT".equals(resultSet.getString("type"));
                    operations.add(deposit
                            ? new CustomerDeposit(amount, occurredAt)
                            : new CustomerWithdrawal(amount, occurredAt));
                }
                return operations;
            }
        }
    }
}
