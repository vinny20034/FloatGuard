package com.floatguard.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * SQL mapper for the {@code sessions} table: opaque bearer tokens issued at
 * login. Deleting the row is what makes a logout take effect immediately.
 */
public class SessionDao {

    public void insert(Connection connection, String token, String userId, Instant createdAt) throws SQLException {
        String sql = "INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.setString(2, userId);
            statement.setString(3, createdAt.toString());
            statement.executeUpdate();
        }
    }

    public Optional<String> findUserIdByToken(Connection connection, String token) throws SQLException {
        String sql = "SELECT user_id FROM sessions WHERE token = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getString("user_id"));
            }
        }
    }

    public void deleteByToken(Connection connection, String token) throws SQLException {
        String sql = "DELETE FROM sessions WHERE token = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.executeUpdate();
        }
    }
}
