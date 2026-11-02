package com.floatguard.api.dto;

/** Response body for {@code POST /admin/agents/{id}/assign-float}. */
public record AssignFloatResponse(String agentId,
                                  String walletId,
                                  String walletType,
                                  double amount,
                                  double balanceAfter) {
}
