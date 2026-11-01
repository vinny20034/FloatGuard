package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.Validation;
import com.floatguard.api.dto.AgentResponse;
import com.floatguard.api.dto.CreateAgentRequest;
import com.floatguard.api.dto.RunwayResponse;
import com.floatguard.api.dto.WalletRunwayResponse;
import com.floatguard.model.integration.Agent;
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
 *   POST /agents             - register an agent
 *   GET  /agents/{id}/runway - balances + runway estimates + warning
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
            Validation.failIfAny(errors);

            ServiceResults.AgentCreation creation =
                    service.registerAgent(request.name().trim(), request.location().trim());

            Agent agent = creation.agent();
            Responses.send(ctx, 201, new AgentResponse(
                    agent.getId(), agent.getName(), agent.getLocation(), creation.createdAt().toString()));
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
                    wallets,
                    runway.overallWarning(),
                    Instant.now().toString()));
        });
    }
}
