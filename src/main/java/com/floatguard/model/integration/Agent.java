package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * A mobile-money agent. Wallets are stored separately (see the {@code wallets}
 * table) and loaded per request, so this type only carries the agent's identity.
 */
public class Agent {

    public static final String AGENT = "AGENT";
    public static final String SUPER_AGENT = "SUPER_AGENT";

    private final String id;
    private final String name;
    private final String location;
    private final String nrcNumber;
    private final String phoneNumber;
    private final String kind;

    public Agent(String id, String name, String location) {
        this(id, name, location, "", "", AGENT);
    }

    public Agent(String id, String name, String location, String nrcNumber,
                 String phoneNumber, String kind) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.nrcNumber = nrcNumber;
        this.phoneNumber = phoneNumber;
        this.kind = kind;
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

    public String getNrcNumber() {
        return nrcNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getKind() {
        return kind;
    }

    public boolean isSuperAgent() {
        return SUPER_AGENT.equals(kind);
    }
}
