package com.floatguard.api;

import com.floatguard.error.ForbiddenException;
import com.floatguard.error.UnauthorizedException;
import com.floatguard.model.auth.Role;
import com.floatguard.model.auth.User;
import com.floatguard.service.AuthService;
import io.javalin.Javalin;
import io.javalin.http.Context;

/**
 * Bearer-token authentication and role guard, applied as a single global
 * {@code before} handler.
 *
 * <p>Only a fixed set of API path prefixes is protected, so static assets (the
 * login page, CSS, JS) are always served. The resolved {@link User} is stashed
 * on the context under {@link #USER_ATTR} for route handlers to read.</p>
 *
 * <p>Rules enforced here:</p>
 * <ul>
 *   <li>{@code /admin/**}         - ADMIN only</li>
 *   <li>{@code /agents} (create)  - ADMIN only</li>
 *   <li>{@code /agents/{id}/**}   - ADMIN, or the agent that owns {@code {id}}</li>
 *   <li>everything else protected - any authenticated user</li>
 * </ul>
 */
public final class AuthMiddleware {

    /** Context attribute holding the authenticated {@link User}. */
    public static final String USER_ATTR = "floatguard.user";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AGENTS_PREFIX = "/agents/";

    private AuthMiddleware() {
    }

    public static void register(Javalin app, AuthService authService) {
        app.before(ctx -> {
            String path = ctx.path();
            if (!isProtected(path)) {
                return;
            }

            User user = authenticate(ctx, authService);
            ctx.attribute(USER_ATTR, user);

            if ((path.equals("/admin") || path.startsWith("/admin/")) && user.getRole() != Role.ADMIN) {
                throw new ForbiddenException("Administrator access is required.");
            }

            if (path.equals("/agents") && user.getRole() != Role.ADMIN) {
                throw new ForbiddenException("Only an administrator can register agents directly.");
            }

            String agentId = agentIdFromPath(path);
            if (agentId != null) {
                requireAgentAccess(user, agentId);
            }
        });
    }

    /** The signed-in user for this request; never {@code null} on a protected route. */
    public static User currentUser(Context ctx) {
        return ctx.attribute(USER_ATTR);
    }

    /** The raw bearer token, or {@code null} when the header is missing/malformed. */
    public static String bearerToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    private static User authenticate(Context ctx, AuthService authService) {
        String token = bearerToken(ctx);
        if (token == null || token.isEmpty()) {
            throw new UnauthorizedException("A bearer token is required.");
        }
        return authService.resolveToken(token)
                .orElseThrow(() -> new UnauthorizedException("Your session is invalid or has expired."));
    }

    private static boolean isProtected(String path) {
        return path.equals("/agents")
                || path.startsWith(AGENTS_PREFIX)
                || path.equals("/deposit")
                || path.equals("/withdraw")
                || path.startsWith("/rebalance/")
                || path.equals("/admin")
                || path.startsWith("/admin/")
                || path.equals("/auth/me")
                || path.equals("/auth/logout");
    }

    /** Extracts {@code {id}} from {@code /agents/{id}/...}, or {@code null}. */
    private static String agentIdFromPath(String path) {
        if (!path.startsWith(AGENTS_PREFIX)) {
            return null;
        }
        String rest = path.substring(AGENTS_PREFIX.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        return id.isEmpty() ? null : id;
    }

    private static void requireAgentAccess(User user, String agentId) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (!agentId.equals(user.getAgentId())) {
            throw new ForbiddenException("You can only access your own agent account.");
        }
    }
}
