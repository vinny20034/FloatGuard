package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.AuthMiddleware;
import com.floatguard.api.dto.RebalanceResponse;
import com.floatguard.service.FloatGuardService;
import com.floatguard.service.ServiceResults;
import io.javalin.Javalin;

/**
 * Administrator approval route for a rebalance request. The service records
 * the requested amount as a wallet deposit and completes the request atomically.
 */
public final class RebalanceRoutes {

    private RebalanceRoutes() {
    }

    public static void register(Javalin app, FloatGuardService service) {

        app.put("/admin/rebalance-requests/{id}/approve", ctx -> {
            var user = AuthMiddleware.currentUser(ctx);
            ServiceResults.RebalanceApproval approval = service.approveRebalance(
                    ctx.pathParam("id"), user.getId(), user.getAgentId());
            var request = approval.request();
            Responses.send(ctx, 200, new RebalanceResponse(
                    request.getId(),
                    request.getWalletId(),
                    approval.type().name(),
                    request.getAmount(),
                    approval.balanceAfter(),
                    approval.sourceBalanceAfter(),
                    request.getStatus().name(),
                    request.getCreatedAt().toString(),
                    request.getCompletedAt() == null ? null : request.getCompletedAt().toString()));
        });

        app.put("/admin/rebalance-requests/{id}/pass", ctx -> {
            var user = AuthMiddleware.currentUser(ctx);
            service.passRebalance(ctx.pathParam("id"), user.getId(), user.getAgentId());
            Responses.send(ctx, 200, java.util.Map.of(
                    "id", ctx.pathParam("id"),
                    "status", "PENDING",
                    "message", "Passed to another super agent in your location."));
        });

        app.delete("/admin/rebalance-requests/{id}", ctx -> {
            var user = AuthMiddleware.currentUser(ctx);
            service.cancelRebalance(ctx.pathParam("id"), user.getAgentId());
            Responses.send(ctx, 200, java.util.Map.of(
                    "id", ctx.pathParam("id"),
                    "status", "DELETED",
                    "message", "Rebalance request deleted."));
        });
    }
}
