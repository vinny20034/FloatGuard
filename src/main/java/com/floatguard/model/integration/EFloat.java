package com.floatguard.model.integration;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * Electronic float held on the mobile-money provider account.
 */
public final class EFloat extends FloatWallet {

    public EFloat(String id, String agentId, double balance) {
        super(id, agentId, balance);
    }

    @Override
    public WalletType getType() {
        return WalletType.EFLOAT;
    }
}
