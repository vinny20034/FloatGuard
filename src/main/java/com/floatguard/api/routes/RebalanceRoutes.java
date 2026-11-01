package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.dto.RebalanceResponse;
import com.floatguard.model.integration.RebalanceRequest;
import com.floatguard.service.FloatGuardService;
import io.javalin.Javalin;

/**
 * Route for rebalance requests:
 *   PUT /rebalance/{id} - mark a request COMPLETED
 *
 * Idempotent: completing an already-completed request returns the original
 * completion timestamp rather than failing.
 */
public final class RebalanceRoutes {

    private RebalanceRoutes() {
    }

    public static void register(Javalin app, FloatGuardService service) {

        app.put("/rebalance/{id}", ctx -> {
            RebalanceRequest request = service.completeRebalance(ctx.pathParam("id"));

            Responses.send(ctx, 200, new RebalanceResponse(
                    request.getId(),
                    request.getWalletId(),
                    request.getStatus().name(),
                    request.getCreatedAt().toString(),
                    request.getCompletedAt() == null ? null : request.getCompletedAt().toString()));
        });
    }
}
