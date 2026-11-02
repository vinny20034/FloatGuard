package com.floatguard.persistence;

import java.sql.Connection;
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
                id           TEXT PRIMARY KEY,
                wallet_id    TEXT NOT NULL,
                status       TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
                created_at   TEXT NOT NULL,
                completed_at TEXT,
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
        } catch (SQLException e) {
            throw new DataAccessException("Failed to initialise the database schema", e);
        }
    }
}
