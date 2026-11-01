package com.floatguard.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runtime configuration, read from environment variables with sensible defaults
 * so the app can be started with zero setup:
 *
 * <pre>
 *   PORT              HTTP port              (default 7000)
 *   FLOATGUARD_DB_URL JDBC url               (default jdbc:sqlite:data/floatguard.db)
 * </pre>
 */
public final class AppConfig {

    private static final String DEFAULT_PORT = "7000";
    private static final String DEFAULT_DB_URL = "jdbc:sqlite:data/floatguard.db";
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private final int port;
    private final String jdbcUrl;

    private AppConfig(int port, String jdbcUrl) {
        this.port = port;
        this.jdbcUrl = jdbcUrl;
    }

    public static AppConfig fromEnvironment() {
        return new AppConfig(
                parsePort(System.getenv().getOrDefault("PORT", DEFAULT_PORT)),
                System.getenv().getOrDefault("FLOATGUARD_DB_URL", DEFAULT_DB_URL));
    }

    public int port() {
        return port;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    /**
     * SQLite creates the database file but not its parent directory, so make sure
     * the folder exists before the first connection is opened.
     */
    public void ensureStorageDirectory() {
        if (!jdbcUrl.startsWith(SQLITE_PREFIX)) {
            return;
        }
        String filePath = jdbcUrl.substring(SQLITE_PREFIX.length());
        if (filePath.isEmpty() || ":memory:".equals(filePath)) {
            return;
        }
        Path parent = Paths.get(filePath).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create database directory: " + parent, e);
        }
    }

    private static int parsePort(String raw) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 1 || parsed > 65_535) {
                throw new NumberFormatException("out of range");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("PORT must be a valid TCP port but was: " + raw, e);
        }
    }
}
