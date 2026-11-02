package com.floatguard.api.dto;

import java.util.List;

/** Response body for {@code GET /agents/{id}/rebalances}. */
public record RebalanceOverviewResponse(String agentId,
                                        double threshold,
                                        List<RebalanceWalletResponse> wallets,
                                        List<RebalanceItemResponse> requests) {
}
