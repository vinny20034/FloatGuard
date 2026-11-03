package com.floatguard.api.dto;

/**
 * Response body for {@code POST /deposit} and {@code POST /withdraw}.
 *
 * @param rebalanceRequestId legacy field retained for API compatibility; agent
 *                           rebalance requests are now submitted separately with
 *                           an amount and float type.
 */
public record OperationResponse(String id,
                                String walletId,
                                String type,
                                double amount,
                                double balanceAfter,
                                double balanceBefore,
                                String timestamp,
                                String rebalanceRequestId,
                                PairedOperationResponse pairedOperation) {

    public record PairedOperationResponse(String id,
                                          String walletId,
                                          String type,
                                          String operationType,
                                          double amount,
                                          double balanceBefore,
                                          double balanceAfter) {
    }
}
