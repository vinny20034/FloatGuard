package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * Physical cash float kept in the agent's till.
 */
public final class CashFloat extends FloatWallet {

    public CashFloat(String id, String agentId, double balance) {
        super(id, agentId, balance);
    }

    @Override
    public WalletType getType() {
        return WalletType.CASH;
    }
}
