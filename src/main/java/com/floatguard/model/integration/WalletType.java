package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * The two floats an agent tracks:
 *   CASH   -> physical cash in the till (CashFloat)
 *   EFLOAT -> electronic float on the mobile-money account (EFloat)
 *
 * Persisted as a TEXT column, so the enum names must stay stable.
 */
public enum WalletType {
    CASH,
    EFLOAT;

    /**
     * Parses a client-supplied wallet type. Kept here (rather than in the REST layer)
     * so the accepted values live next to the type itself.
     *
     * @return the matching type, or {@code null} when the value is not recognised
     *         (the caller decides how to surface that as a validation error).
     */
    public static WalletType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String normalised = raw.trim().toUpperCase().replace("-", "").replace("_", "");
        return switch (normalised) {
            case "CASH", "CASHFLOAT" -> CASH;
            case "EFLOAT", "ELECTRONIC", "ELECTRONICFLOAT" -> EFLOAT;
            default -> null;
        };
    }
}
