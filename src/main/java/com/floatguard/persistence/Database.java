package com.floatguard.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns all JDBC connection handling. DAOs never open connections themselves -
 * they receive one, which makes transaction boundaries explicit and keeps the
 * SQL mappers free of connection lifecycle code.
 *
 * <p>SQLite specifics applied to every connection:</p>
 * <ul>
 *   <li>{@code foreign_keys = ON} - SQLite disables FK enforcement by default, and
 *       the pragma is per-connection, so it must be set each time.</li>
 *   <li>{@code busy_timeout = 5000} - makes concurrent writers wait for the lock
 *       instead of failing immediately with "database is locked".</li>
 * </ul>
 */
public final class Database {

    private final String jdbcUrl;

    public Database(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    /** Opens a ready-to-use connection with the SQLite pragmas already applied. */
    public Connection open() {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            return connection;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to open database connection to " + jdbcUrl, e);
        }
    }

    /** Read-only unit of work; the connection is closed for you. */
    public <T> T read(SqlWork<T> work) {
        try (Connection connection = open()) {
            return work.execute(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Database read failed", e);
        }
    }

    /**
     * Runs a unit of work inside a single transaction. Commits on success, rolls
     * back on any failure. This is how balance updates and their operation rows
     * are kept consistent: either both are written or neither is.
     */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = open()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                // Infrastructure failure -> wrap it.
                safeRollback(connection);
                throw new DataAccessException("Transaction failed and was rolled back", e);
            } catch (RuntimeException e) {
                // Business exceptions (NotFound / Conflict / InsufficientFloat) must
                // propagate unchanged so the API layer can map them to the right status.
                safeRollback(connection);
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to manage transaction", e);
        }
    }

    private void safeRollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Nothing useful to do: the original failure is already being propagated.
        }
    }

    /** A unit of work that may throw {@link SQLException}. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
