package com.floatguard.api.dto;

/** Request body for {@code POST /agents/{id}/rebalances}. */
public record CreateRebalanceRequest(String type) {
}
