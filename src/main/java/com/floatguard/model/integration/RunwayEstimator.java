package com.floatguard.model.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * ============================================================================
 * INTEGRATION CONTRACT (owned by the DOMAIN team)
 * ============================================================================
 * Runway + warning logic. Lives in the domain layer on purpose: the REST layer
 * only calls {@link #estimate(FloatWallet, List)} and serialises the result.
 *
 * <p><strong>Reference implementation.</strong> The algorithm and the warning
 * thresholds below are placeholders that the domain owner is expected to tune
 * (or replace entirely). The signatures are the contract the REST layer depends on.</p>
 *
 * <p>Runway = current balance / average daily net outflow.</p>
 */
public final class RunwayEstimator {

    /** Observation window used to smooth the daily outflow (in days). */
    private static final double MIN_OBSERVED_DAYS = 1.0;
    private static final double MAX_OBSERVED_DAYS = 30.0;

    /** Warning thresholds (in days) - domain-owned placeholders. */
    private static final double CRITICAL_DAYS = 1.0;
    private static final double WARNING_DAYS = 3.0;

    private static final String OK = "OK";
    private static final String WARNING = "WARNING";
    private static final String CRITICAL = "CRITICAL";

    private RunwayEstimator() {
    }

    /**
     * Estimates how many days of runway a wallet has left, based on the recent
     * operations recorded against it.
     *
     * @param wallet  the wallet being assessed
     * @param history operations for that wallet (any order)
     * @return an immutable estimate; {@code runwayDays} is {@code null} when there
     *         is no net outflow (the float is not being drained)
     */
    public static RunwayEstimate estimate(FloatWallet wallet, List<Operation> history) {
        double dailyNetOutflow = averageDailyNetOutflow(history);
        Double runwayDays = dailyNetOutflow <= 0
                ? null
                : round(wallet.getBalance() / dailyNetOutflow);
        return new RunwayEstimate(wallet.getId(), wallet.getType(), wallet.getBalance(), runwayDays, warningFor(runwayDays));
    }

    /** Returns the most severe warning across all wallets (OK < WARNING < CRITICAL). */
    public static String overallWarning(List<RunwayEstimate> estimates) {
        return estimates.stream()
                .map(RunwayEstimate::warning)
                .max((left, right) -> Integer.compare(rank(left), rank(right)))
                .orElse(OK);
    }

    /**
     * Average net daily outflow = (total withdrawals - total deposits) spread over
     * the number of days observed. A negative value means the float is growing.
     */
    private static double averageDailyNetOutflow(List<Operation> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        Instant earliest = history.stream()
                .map(Operation::getTimestamp)
                .min(Instant::compareTo)
                .orElse(null);
        if (earliest == null) {
            return 0;
        }
        double observedSeconds = Duration.between(earliest, Instant.now()).getSeconds();
        double observedDays = Math.min(MAX_OBSERVED_DAYS, Math.max(MIN_OBSERVED_DAYS, observedSeconds / 86_400.0));
        double netOutflow = history.stream().mapToDouble(operation -> -operation.signedAmount()).sum();
        return netOutflow / observedDays;
    }

    private static String warningFor(Double runwayDays) {
        if (runwayDays == null) {
            return OK;
        }
        if (runwayDays < CRITICAL_DAYS) {
            return CRITICAL;
        }
        if (runwayDays < WARNING_DAYS) {
            return WARNING;
        }
        return OK;
    }

    private static int rank(String warning) {
        return switch (warning) {
            case CRITICAL -> 2;
            case WARNING -> 1;
            default -> 0;
        };
    }

    /** Rounds to one decimal place so JSON payloads stay readable. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
