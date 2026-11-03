package com.floatguard.api.dto;

/** Response body for an administrator's rebalance approval. */
public record RebalanceResponse(String id,
                                String walletId,
                                String walletType,
                                double amount,
                                double balanceAfter,
                                double sourceBalanceAfter,
                                String status,
                                String createdAt,
                                String completedAt) {
}
