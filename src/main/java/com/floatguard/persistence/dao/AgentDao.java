package com.floatguard.persistence.dao;

import com.floatguard.model.integration.Agent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * SQL mapper for the {@code agents} table. Methods receive an existing
 * {@link Connection} so they take part in the caller's transaction (see
 * {@link com.floatguard.persistence.Database#inTransaction}).
 */
public class AgentDao {

    public void insert(Connection connection, Agent agent, Instant createdAt) throws SQLException {
        String sql = "INSERT INTO agents (id, name, location, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, agent.getId());
            statement.setString(2, agent.getName());
            statement.setString(3, agent.getLocation());
            statement.setString(4, createdAt.toString());
            statement.executeUpdate();
        }
    }

    /** Returns the agent without its wallets - callers load wallets explicitly. */
    public Optional<Agent> findById(Connection connection, String agentId) throws SQLException {
        String sql = "SELECT id, name, location FROM agents WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, agentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Agent(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("location")));
            }
        }
    }

    public boolean existsById(Connection connection, String agentId) throws SQLException {
        String sql = "SELECT 1 FROM agents WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, agentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
