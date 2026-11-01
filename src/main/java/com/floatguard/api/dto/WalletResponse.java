package com.floatguard.api.dto;

/** Response body for {@code POST /agents/{id}/wallets}. */
public record WalletResponse(String id, String agentId, String type, double balance, String createdAt) {
}
