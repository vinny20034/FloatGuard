package com.floatguard.api.dto;

/**
 * Per-wallet section of the runway response.
 *
 * @param runwayDays estimated days of runway; {@code null} means "no net outflow
 *                   observed" (effectively unlimited)
 * @param warning    OK | WARNING | CRITICAL
 */
public record WalletRunwayResponse(String walletId,
                                   String type,
                                   double balance,
                                   Double runwayDays,
                                   String warning) {
}
