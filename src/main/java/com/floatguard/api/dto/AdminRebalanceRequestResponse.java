package com.floatguard.api.dto;

/** Rebalance request enriched with the requesting agent's display details. */
public record AdminRebalanceRequestResponse(String id,
                                           String agentId,
                                           String agentName,
                                           String agentPhoneNumber,
                                           String location,
                                           String area,
                                           String walletType,
                                           double amount,
                                           String status,
                                           int handoffCount,
                                           String createdAt,
                                           String completedAt) {
}
