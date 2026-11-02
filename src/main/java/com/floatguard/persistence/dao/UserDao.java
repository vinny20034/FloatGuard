package com.floatguard.persistence.dao;

import com.floatguard.model.auth.Role;
import com.floatguard.model.auth.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * SQL mapper for the {@code users} table. Usernames are stored lower-cased by
 * the auth service, so the {@code UNIQUE} constraint is case-insensitive in
 * practice.
 */
public class UserDao {

    public void insert(Connection connection, User user, Instant createdAt) throws SQLException {
        String sql = "INSERT INTO users (id, username, password_hash, role, agent_id, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, user.getRole().name());
            statement.setString(5, user.getAgentId());
            statement.setString(6, createdAt.toString());
            statement.executeUpdate();
        }
    }

    public Optional<User> findById(Connection connection, String userId) throws SQLException {
        return find(connection, "SELECT id, username, password_hash, role, agent_id FROM users WHERE id = ?", userId);
    }

    public Optional<User> findByUsername(Connection connection, String username) throws SQLException {
        return find(connection,
                "SELECT id, username, password_hash, role, agent_id FROM users WHERE username = ?", username);
    }

    public boolean existsByUsername(Connection connection, String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Optional<User> find(Connection connection, String sql, String parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new User(
                        resultSet.getString("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        Role.valueOf(resultSet.getString("role")),
                        resultSet.getString("agent_id")));
            }
        }
    }
}
