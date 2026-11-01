package com.floatguard.api.dto;

/**
 * Request body for {@code POST /agents/{id}/wallets}.
 *
 * @param type           "CASH" or "EFLOAT"
 * @param openingBalance optional opening balance; defaults to 0 when omitted
 */
public record CreateWalletRequest(String type, Double openingBalance) {
}
