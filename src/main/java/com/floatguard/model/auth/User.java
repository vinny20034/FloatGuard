package com.floatguard.model.auth;

/**
 * An account that can sign in. Admins are not tied to an agent; an agent user
 * always points at the single {@code agents} row it owns.
 *
 * <p>The password is only ever held as a bcrypt hash - the plain value never
 * leaves the registration/login flow.</p>
 */
public class User {

    private final String id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final String agentId;

    public User(String id, String username, String passwordHash, Role role, String agentId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.agentId = agentId;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    /** The agent this user owns, or {@code null} for an ADMIN. */
    public String getAgentId() {
        return agentId;
    }
}
