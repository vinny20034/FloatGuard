package com.floatguard.api.dto;

/** Response body for {@code PUT /rebalance/{id}}. */
public record RebalanceResponse(String id,
                                String walletId,
                                String status,
                                String createdAt,
                                String completedAt) {
}
