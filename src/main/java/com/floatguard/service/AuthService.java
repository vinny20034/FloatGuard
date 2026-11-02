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
    private final String adminInviteCode;

    public AuthService(Database database,
                       UserDao userDao,
                       SessionDao sessionDao,
                       AgentDao agentDao,
                       WalletDao walletDao,
                       String adminInviteCode) {
        this.database = database;
        this.userDao = userDao;
        this.sessionDao = sessionDao;
        this.agentDao = agentDao;
        this.walletDao = walletDao;
        this.adminInviteCode = adminInviteCode;
    }

    /**
     * POST /auth/register.
     *
     * <p>An AGENT account also creates the agent record and both of its wallets,
     * in the same transaction. Wallet balances start at zero: an administrator
     * assigns the opening float afterwards.</p>
     */
    public ServiceResults.Authenticated register(String username,
                                                 String password,
                                                 Role role,
                                                 String inviteCode,
                                                 String agentName,
                                                 String location) {
        String normalisedUsername = username.trim().toLowerCase(Locale.ROOT);
        String suppliedCode = inviteCode == null ? "" : inviteCode.trim();

        if (role == Role.ADMIN && !adminInviteCode.equals(suppliedCode)) {
            throw new ForbiddenException("A valid admin invite code is required to create an administrator account.");
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        Instant createdAt = Instant.now();

        User user = database.inTransaction(connection -> {
            if (userDao.existsByUsername(connection, normalisedUsername)) {
                throw new ConflictException("Username '" + normalisedUsername + "' is already taken.");
            }

            String agentId = null;
            if (role == Role.AGENT) {
                agentId = UUID.randomUUID().toString();
                agentDao.insert(connection, new Agent(agentId, agentName.trim(), location.trim()), createdAt);
                walletDao.insert(connection,
                        FloatWallet.create(WalletType.CASH, UUID.randomUUID().toString(), agentId, 0), createdAt);
                walletDao.insert(connection,
                        FloatWallet.create(WalletType.EFLOAT, UUID.randomUUID().toString(), agentId, 0), createdAt);
            }

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
