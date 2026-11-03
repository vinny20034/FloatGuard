package com.floatguard.persistence;

import com.floatguard.model.integration.FloatWallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the schema on startup. Every statement is idempotent
 * ({@code CREATE ... IF NOT EXISTS}), so running it on an existing database is
 * safe and no separate migration tool is required for this scope.
 */
public final class SchemaInitializer {

    /**
     * DDL executed once at startup. Order matters: parents (agents) before
     * children (wallets, operations, rebalance_requests) because of the foreign keys.
     */
    private static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS agents (
                id         TEXT PRIMARY KEY,
                name       TEXT NOT NULL,
                location   TEXT NOT NULL,
                nrc_number TEXT NOT NULL DEFAULT '',
                phone_number TEXT NOT NULL DEFAULT '',
                kind       TEXT NOT NULL DEFAULT 'AGENT' CHECK (kind IN ('AGENT', 'SUPER_AGENT')),
                created_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS wallets (
                id         TEXT PRIMARY KEY,
                agent_id   TEXT NOT NULL,
                type       TEXT NOT NULL CHECK (type IN ('CASH', 'EFLOAT')),
                balance    REAL NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
                UNIQUE (agent_id, type)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS operations (
                id            TEXT PRIMARY KEY,
                wallet_id     TEXT NOT NULL,
                type          TEXT NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
                amount        REAL NOT NULL CHECK (amount > 0),
                balance_after REAL NOT NULL,
                occurred_at   TEXT NOT NULL,
                FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS rebalance_requests (
                id              TEXT PRIMARY KEY,
                wallet_id       TEXT NOT NULL,
                requested_amount REAL NOT NULL DEFAULT 0 CHECK (requested_amount >= 0),
                area            TEXT NOT NULL DEFAULT '',
                status          TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
                created_at      TEXT NOT NULL,
                completed_at    TEXT,
                FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS users (
                id            TEXT PRIMARY KEY,
                username      TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role          TEXT NOT NULL CHECK (role IN ('ADMIN', 'AGENT')),
                agent_id      TEXT,
                created_at    TEXT NOT NULL,
                FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sessions (
                token      TEXT PRIMARY KEY,
                user_id    TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
            )
            """,
            // Indexes: lookups are almost always "by agent" or "by wallet".
            "CREATE INDEX IF NOT EXISTS idx_wallets_agent ON wallets (agent_id)",
            "CREATE INDEX IF NOT EXISTS idx_operations_wallet ON operations (wallet_id)",
            "CREATE INDEX IF NOT EXISTS idx_operations_wallet_time ON operations (wallet_id, occurred_at)",
            "CREATE INDEX IF NOT EXISTS idx_rebalance_wallet ON rebalance_requests (wallet_id)",
            "CREATE INDEX IF NOT EXISTS idx_users_agent ON users (agent_id)",
            "CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions (user_id)"
    };

    private SchemaInitializer() {
    }

    public static void initialize(Database database) {
        try (Connection connection = database.open();
             Statement statement = connection.createStatement()) {
            // WAL improves read/write concurrency and is persisted in the database file.
            statement.execute("PRAGMA journal_mode = WAL");
            for (String ddl : DDL) {
                statement.execute(ddl);
            }
            ensureAgentProfileColumns(connection);
            ensureRequestedAmountColumn(connection);
            ensureRebalanceAreaColumn(connection);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS rebalance_request_passes (
                        request_id TEXT NOT NULL,
                        super_agent_user_id TEXT NOT NULL,
                        passed_at TEXT NOT NULL,
                        PRIMARY KEY (request_id, super_agent_user_id),
                        FOREIGN KEY (request_id) REFERENCES rebalance_requests (id) ON DELETE CASCADE,
                        FOREIGN KEY (super_agent_user_id) REFERENCES users (id) ON DELETE CASCADE
                    )
                    """);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to initialise the database schema", e);
        }

    }

    private static void ensureAgentProfileColumns(Connection connection) throws SQLException {
        boolean hasNrc = false;
        boolean hasPhone = false;
        boolean hasKind = false;
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(agents)")) {
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                hasNrc |= "nrc_number".equals(name);
                hasPhone |= "phone_number".equals(name);
                hasKind |= "kind".equals(name);
            }
        }
        try (Statement statement = connection.createStatement()) {
            if (!hasNrc) {
                statement.execute("ALTER TABLE agents ADD COLUMN nrc_number TEXT NOT NULL DEFAULT ''");
            }
            if (!hasPhone) {
                statement.execute("ALTER TABLE agents ADD COLUMN phone_number TEXT NOT NULL DEFAULT ''");
            }
            if (!hasKind) {
                statement.execute("ALTER TABLE agents ADD COLUMN kind TEXT NOT NULL DEFAULT 'AGENT'");
            }
        }
    }

    private static void ensureRebalanceAreaColumn(Connection connection) throws SQLException {
        boolean hasArea = false;
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(rebalance_requests)")) {
            while (resultSet.next()) {
                if ("area".equals(resultSet.getString("name"))) {
                    hasArea = true;
                    break;
                }
            }
        }

        if (!hasArea) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE rebalance_requests "
                        + "ADD COLUMN area TEXT NOT NULL DEFAULT ''");
            }
        }
    }

    private static void ensureRequestedAmountColumn(Connection connection) throws SQLException {
        boolean hasAmount = false;
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(rebalance_requests)")) {
            while (resultSet.next()) {
                if ("requested_amount".equals(resultSet.getString("name"))) {
                    hasAmount = true;
                    break;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            if (!hasAmount) {
                statement.execute("ALTER TABLE rebalance_requests ADD COLUMN "
                        + "requested_amount REAL NOT NULL DEFAULT 0 CHECK (requested_amount >= 0)");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE rebalance_requests
                    SET requested_amount = MAX(0.01, ? - COALESCE(
                        (SELECT balance FROM wallets WHERE wallets.id = rebalance_requests.wallet_id), 0))
                    WHERE requested_amount = 0
                    """)) {
            statement.setDouble(1, FloatWallet.REBALANCE_THRESHOLD);
            statement.executeUpdate();
        }
    }
}
