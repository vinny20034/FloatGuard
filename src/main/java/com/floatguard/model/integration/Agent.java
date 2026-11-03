package com.floatguard.model.integration;

import java.util.Objects;

/**
 * A mobile-money agent. Carries only identity: wallets are stored separately
 * and loaded per request. The class is immutable (private final fields, no setters).
 * Two agents are equal when they have the same id.
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Agent)) return false;
        Agent other = (Agent) o;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Agent{id=" + id + ", name=" + name + ", location=" + location + "}";
    }
}
