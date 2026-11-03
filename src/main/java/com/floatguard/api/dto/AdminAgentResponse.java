package com.floatguard.api.dto;

import java.util.List;

/** One agent row in the admin monitoring view ({@code GET /admin/agents}). */
public record AdminAgentResponse(String id,
                                 String name,
                                 String location,
                                 String phoneNumber,
                                 List<WalletRunwayResponse> wallets,
                                 String overallWarning) {
}
