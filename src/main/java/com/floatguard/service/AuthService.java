package com.floatguard.service;

import com.floatguard.error.ConflictException;
import com.floatguard.error.ForbiddenException;
import com.floatguard.error.UnauthorizedException;
import com.floatguard.model.auth.Role;
import com.floatguard.model.auth.User;
import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.WalletType;
import com.floatguard.persistence.Database;
import com.floatguard.persistence.dao.AgentDao;
import com.floatguard.persistence.dao.SessionDao;
import com.floatguard.persistence.dao.UserDao;
import com.floatguard.persistence.dao.WalletDao;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registration, login and opaque bearer-token sessions.
 *
 * <p>Kept separate from {@link FloatGuardService} so float rules stay untouched
 * by account concerns. Passwords are only ever stored as bcrypt hashes, and a
 * session is revoked simply by deleting its row.</p>
 */
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final Database database;
    private final UserDao userDao;
    private final SessionDao sessionDao;
    private final AgentDao agentDao;
    private final WalletDao walletDao;
    private final Set<String> adminInviteCodes;

    public AuthService(Database database,
                       UserDao userDao,
                       SessionDao sessionDao,
                       AgentDao agentDao,
                       WalletDao walletDao,
                       Set<String> adminInviteCodes) {
        this.database = database;
        this.userDao = userDao;
        this.sessionDao = sessionDao;
        this.agentDao = agentDao;
        this.walletDao = walletDao;
        this.adminInviteCodes = Set.copyOf(adminInviteCodes);
    }

    /**
     * POST /auth/register.
     *
     * <p>An AGENT account also creates the agent record and both of its wallets,
    * in the same transaction, using the opening balances declared during signup.</p>
     */
    public ServiceResults.Authenticated register(String username,
                                                 String password,
                                                 Role role,
                                                 String inviteCode,
                                                 String agentName,
                                                 String location,
                                                 String nrcNumber,
                                                 String phoneNumber,
                                                 Double openingCash,
                                                 Double openingEFloat) {
        String normalisedUsername = username.trim().toLowerCase(Locale.ROOT);
        String suppliedCode = inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);

        if (role == Role.ADMIN && !adminInviteCodes.contains(suppliedCode)) {
            throw new ForbiddenException("Use a valid administrator invite code to create an administrator account.");
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        Instant createdAt = Instant.now();

        User user = database.inTransaction(connection -> {
            if (userDao.existsByUsername(connection, normalisedUsername)) {
                throw new ConflictException("Username '" + normalisedUsername + "' is already taken.");
            }

            String agentId = UUID.randomUUID().toString();
            String kind = role == Role.ADMIN ? Agent.SUPER_AGENT : Agent.AGENT;
            Agent profile = new Agent(agentId, agentName.trim(), location.trim(), nrcNumber.trim(),
                    phoneNumber.trim(), kind);
            agentDao.insert(connection, profile, createdAt);
            walletDao.insert(connection,
                    FloatWallet.create(WalletType.CASH, UUID.randomUUID().toString(), agentId, openingCash), createdAt);
            walletDao.insert(connection,
                    FloatWallet.create(WalletType.EFLOAT, UUID.randomUUID().toString(), agentId, openingEFloat), createdAt);

            User created = new User(UUID.randomUUID().toString(), normalisedUsername, passwordHash, role, agentId);
            userDao.insert(connection, created, createdAt);
            return created;
        });

        return new ServiceResults.Authenticated(user, startSession(user.getId()));
    }

    /** POST /auth/login. */
    public ServiceResults.Authenticated login(String username, String password) {
        String normalisedUsername = username.trim().toLowerCase(Locale.ROOT);

        User user = database.read(connection ->
                userDao.findByUsername(connection, normalisedUsername).orElse(null));

        // One message for both "no such user" and "wrong password" so the response
        // cannot be used to discover which usernames exist.
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Incorrect username or password.");
        }

        return new ServiceResults.Authenticated(user, startSession(user.getId()));
    }

    /** Resolves a bearer token to its user, or empty when the token is unknown. */
    public Optional<User> resolveToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return database.read(connection -> {
            Optional<String> userId = sessionDao.findUserIdByToken(connection, token);
            if (userId.isEmpty()) {
                return Optional.empty();
            }
            return userDao.findById(connection, userId.get());
        });
    }

    /** True only when this super agent is allowed to manage an ordinary agent in its location. */
    public boolean canSuperAgentAccessAgent(String superAgentId, String agentId) {
        if (superAgentId == null) {
            return false;
        }
        return database.read(connection -> {
            Agent superAgent = agentDao.findById(connection, superAgentId).orElse(null);
            Agent agent = agentDao.findById(connection, agentId).orElse(null);
            return superAgent != null && superAgent.isSuperAgent()
                    && agent != null && !agent.isSuperAgent()
                    && superAgent.getLocation().trim().equalsIgnoreCase(agent.getLocation().trim());
        });
    }

    /** POST /auth/logout - removes the session so the token can no longer be used. */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        database.inTransaction(connection -> {
            sessionDao.deleteByToken(connection, token);
            return null;
        });
    }

    private String startSession(String userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant createdAt = Instant.now();

        database.inTransaction(connection -> {
            sessionDao.insert(connection, token, userId, createdAt);
            return null;
        });
        return token;
    }
}
