package com.floatguard.model.auth;

/**
 * The two kinds of account in FloatGuard.
 *
 * <p>Persisted as a TEXT column, so the enum names must stay stable.</p>
 */
public enum Role {
    /** Oversees every agent and assigns float. */
    ADMIN,

    /** Owns exactly one agent record and its two wallets. */
    AGENT;

    /** Parses a client-supplied role name, or {@code null} when unrecognised. */
    public static Role fromString(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "ADMIN" -> ADMIN;
            case "AGENT" -> AGENT;
            default -> null;
        };
    }
}
