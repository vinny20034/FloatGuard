package com.floatguard.api.dto;

import java.util.List;

/** Response body for {@code GET /agents/{id}/runway}. */
public record RunwayResponse(String agentId,
                             String agentName,
                             String location,
                             List<WalletRunwayResponse> wallets,
                             String overallWarning,
                             String generatedAt) {
}
