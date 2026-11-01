package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * Immutable result of a runway estimate for one wallet.
 *
 * @param walletId   wallet the estimate belongs to
 * @param walletType CASH or EFLOAT
 * @param balance    current balance at the time of the estimate
 * @param runwayDays estimated days until the float is exhausted; {@code null}
 *                   means "effectively unlimited" (no net outflow observed)
 * @param warning    domain-provided severity label: OK | WARNING | CRITICAL
 */
public record RunwayEstimate(
        String walletId,
        WalletType walletType,
        double balance,
        Double runwayDays,
        String warning) {
}
