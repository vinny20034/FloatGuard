package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.AuthMiddleware;
import com.floatguard.api.Validation;
import com.floatguard.api.dto.AgentResponse;
import com.floatguard.api.dto.CreateAgentRequest;
import com.floatguard.api.dto.CreateRebalanceRequest;
import com.floatguard.api.dto.RebalanceItemResponse;
import com.floatguard.api.dto.RebalanceOverviewResponse;
import com.floatguard.api.dto.RebalanceWalletResponse;
import com.floatguard.api.dto.RunwayResponse;
import com.floatguard.api.dto.WalletRunwayResponse;
import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.WalletType;
import com.floatguard.service.FloatGuardService;
import com.floatguard.service.ServiceResults;
import com.floatguard.util.Json;
import io.javalin.Javalin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes for the agent resource:
 *   POST /agents                - register an agent
 *   GET  /agents/{id}/runway    - balances + runway estimates + warning
 *   GET  /agents/{id}/rebalances - floats vs threshold + the agent's requests
 *   POST /agents/{id}/rebalances - raise a PENDING request for one float
 */
public final class AgentRoutes {

    private AgentRoutes() {
    }

    public static void register(Javalin app, FloatGuardService service) {

        app.post("/agents", ctx -> {
            CreateAgentRequest request = Json.read(ctx.body(), CreateAgentRequest.class);

            Map<String, String> errors = new LinkedHashMap<>();
            Validation.requireNonBlank(errors, "name", request.name());
            Validation.requireNonBlank(errors, "location", request.location());
            Validation.requireNonBlank(errors, "nrcNumber", request.nrcNumber());
            Validation.requireNonBlank(errors, "phoneNumber", request.phoneNumber());
            Validation.failIfAny(errors);

            var user = AuthMiddleware.currentUser(ctx);
            ServiceResults.AgentCreation creation =
                    service.registerAgent(request.name().trim(), request.location().trim(),
                            request.nrcNumber().trim(), request.phoneNumber().trim(), user.getAgentId());

            Agent agent = creation.agent();
            Responses.send(ctx, 201, new AgentResponse(
                    agent.getId(), agent.getName(), agent.getLocation(), agent.getPhoneNumber(),
                    creation.createdAt().toString()));
        });

        app.get("/agents/{id}/runway", ctx -> {
            ServiceResults.AgentRunway runway = service.getRunway(ctx.pathParam("id"));

            List<WalletRunwayResponse> wallets = new ArrayList<>();
            runway.estimates().forEach(estimate -> wallets.add(new WalletRunwayResponse(
                    estimate.walletId(),
                    estimate.walletType().name(),
                    estimate.balance(),
                    estimate.runwayDays(),
                    estimate.warning())));

            Agent agent = runway.agent();
            Responses.send(ctx, 200, new RunwayResponse(
                    agent.getId(),
                    agent.getName(),
                    agent.getLocation(),
                    agent.getPhoneNumber(),
                    wallets,
                    runway.overallWarning(),
                    Instant.now().toString()));
        });

        app.get("/agents/{id}/rebalances", ctx -> {
            ServiceResults.RebalanceOverview overview = service.getRebalanceOverview(ctx.pathParam("id"));

            List<RebalanceWalletResponse> wallets = new ArrayList<>();
            overview.wallets().forEach(wallet -> wallets.add(new RebalanceWalletResponse(
                    wallet.type().name(), wallet.balance(), wallet.belowThreshold())));

            List<RebalanceItemResponse> requests = new ArrayList<>();
            overview.requests().forEach(request -> requests.add(toItem(request)));

            Responses.send(ctx, 200, new RebalanceOverviewResponse(
                    overview.agentId(), overview.threshold(), wallets, requests));
        });

        app.post("/agents/{id}/rebalances", ctx -> {
            CreateRebalanceRequest request = Json.read(ctx.body(), CreateRebalanceRequest.class);

            Map<String, String> errors = new LinkedHashMap<>();
            WalletType type = WalletType.fromString(request.type());
            if (type == null) {
                errors.put("type", "type must be CASH or EFLOAT.");
            }
            Validation.requirePositiveAmount(errors, "amount", request.amount());
            Validation.requireNonBlank(errors, "area", request.area());
            Validation.failIfAny(errors);

            ServiceResults.RebalanceView view =
                    service.requestRebalance(ctx.pathParam("id"), type, request.amount(), request.area().trim());
            Responses.send(ctx, 201, toItem(view));
        });
    }

    private static RebalanceItemResponse toItem(ServiceResults.RebalanceView view) {
        return new RebalanceItemResponse(
                view.id(),
                view.type().name(),
                view.amount(),
                view.area(),
                view.status(),
                view.handoffCount(),
                view.createdAt(),
                view.completedAt());
    }
}
