package com.floatguard.api.dto;

/**
 * One float inside the rebalance overview.
 *
 * @param belowThreshold true when the balance has fallen under the rebalance threshold
 */
public record RebalanceWalletResponse(String type, double balance, boolean belowThreshold) {
}
