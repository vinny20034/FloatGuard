package com.floatguard.api.dto;

/**
 * Response body for {@code POST /deposit} and {@code POST /withdraw}.
 *
 * @param rebalanceRequestId id of the outstanding PENDING rebalance request for the
 *                           wallet, or {@code null} when the float is healthy. Use it
 *                           with {@code PUT /rebalance/{id}}.
 */
public record OperationResponse(String id,
                                String walletId,
                                String type,
                                double amount,
                                double balanceAfter,
                                double balanceBefore,
                                String timestamp,
                                String rebalanceRequestId) {
}
