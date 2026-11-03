package com.floatguard.model.auth;

/**
 * An account that can sign in. Both agents and super agents point at the
 * operational profile and wallets they own.
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

    /** The operational profile this user owns. */
    public String getAgentId() {
        return agentId;
    }
}
