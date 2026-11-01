package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * A mobile-money agent. Wallets are stored separately (see the {@code wallets}
 * table) and loaded per request, so this type only carries the agent's identity.
 */
public class Agent {

    private final String id;
    private final String name;
    private final String location;

    public Agent(String id, String name, String location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }
}
