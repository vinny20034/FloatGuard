package com.floatguard.api.dto;

/**
 * A rebalance request as the client displays it. The wallet is identified by its
 * type (CASH / EFLOAT) rather than its id, so the UI never shows a raw UUID.
 */
public record RebalanceItemResponse(String id,
                                    String type,
                                    double amount,
                                    String area,
                                    String status,
                                    int handoffCount,
                                    String createdAt,
                                    String completedAt) {
}
