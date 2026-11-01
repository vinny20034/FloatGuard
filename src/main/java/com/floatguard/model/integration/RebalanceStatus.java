package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * Lifecycle of a {@link RebalanceRequest}. Persisted as TEXT.
 */
public enum RebalanceStatus {
    PENDING,
    COMPLETED
}
