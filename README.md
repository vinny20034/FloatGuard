# FloatGuard

FloatGuard helps mobile-money agents in Zambia keep track of the two floats they manage,
and lets an administrator watch every agent and top up floats before they run dry:

- **CashFloat** — physical cash in the till
- **EFloat** — electronic float held on the mobile-money provider account

Balances are entered **manually** (there is no live connection to a provider). Everything is
stored in SQLite so state survives restarts, and one Java process serves both the REST API
and three HTML pages:

| Page | Who uses it | Purpose |
|---|---|---|
| `/index.html` | everyone | sign in / create an account |
| `/agent.html` | AGENT | record deposits & withdrawals, watch runway, request rebalances |
| `/admin.html` | ADMIN | monitor every agent, assign float |

There are two account roles:

- **AGENT** — owns exactly one agent record and its two wallets (CASH + EFLOAT).
- **ADMIN** — not tied to an agent; oversees all agents and tops up their floats.

---

## 1. Tech stack

| Concern | Choice |
|---|---|
| Language | Java 17 (builds with any JDK ≥ 17) |
| HTTP server | Javalin 6.3.0 (Jetty 11) |
| Persistence | SQLite via `org.xerial:sqlite-jdbc` (plain JDBC, no ORM) |
| JSON | Jackson (`jackson-databind`) |
| Auth | `jbcrypt` password hashing + opaque `SecureRandom` bearer tokens |
| Logging | slf4j-simple |
| Build | Maven (fat jar via `maven-shade-plugin`) |

The frontend is deliberately vanilla HTML/CSS/JS — no frameworks, no build step, no external
dependencies (fonts are the only remote asset).

---

## 2. Quick start

```bash
# 1. Build a runnable jar  ->  target/floatguard.jar
mvn clean package

# 2. Run it (defaults: port 7000, DB at ./data/floatguard.db)
java -jar target/floatguard.jar
```

Then open <http://localhost:7000/> in a browser.

> If `mvn` is not installed, any recent Maven will do — point it at this `pom.xml`.

### Try it out

1. Open <http://localhost:7000/> and switch to **Create account**.
2. Register an **Administrator** using the invite code `FLOATGUARD-ADMIN` (see config below).
3. Sign out, then register an **Agent** (choose business name + location). The agent record and
   both wallets are created automatically with a zero balance.
4. Sign in as the administrator and use **Assign float** to top up the agent's cash/e-float.
5. Sign in as the agent to record deposits/withdrawals and watch the runway warnings.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `7000` | HTTP port to listen on |
| `FLOATGUARD_DB_URL` | `jdbc:sqlite:data/floatguard.db` | JDBC URL of the SQLite database |
| `FLOATGUARD_ADMIN_INVITE_CODE` | `FLOATGUARD-ADMIN` | Code required to self-register an ADMIN account |

```bash
PORT=8080 FLOATGUARD_DB_URL="jdbc:sqlite:/tmp/floatguard.db" \
  FLOATGUARD_ADMIN_INVITE_CODE="my-secret-code" \
  java -jar target/floatguard.jar
```

The SQLite schema is created automatically on startup if it does not exist, and the parent
directory of the database file is created if missing.

---

## 3. Project structure

```
src/main/java/com/floatguard/
├── Main.java                     entry point: config -> schema -> services -> routes -> listen
├── config/AppConfig.java         reads PORT / FLOATGUARD_DB_URL / FLOATGUARD_ADMIN_INVITE_CODE
├── api/                          REST layer — validate, delegate, map to JSON (NO business rules)
│   ├── ApiRoutes.java            registers error mapping + auth guard + all route groups
│   ├── AuthMiddleware.java       bearer-token authentication + role/ownership guard
│   ├── ErrorMapper.java          exception -> HTTP status translation
│   ├── Responses.java            JSON response / error-envelope helpers
│   ├── Validation.java           input validation helpers
│   ├── dto/                      request/response records (the wire shapes)
│   └── routes/                   one handler per resource
│       ├── AuthRoutes.java       /auth/register, /auth/login, /auth/logout, /auth/me
│       ├── AdminRoutes.java      /admin/agents, /admin/agents/{id}/assign-float
│       ├── AgentRoutes.java      POST /agents, runway + rebalance read/write
│       ├── WalletRoutes.java     POST /agents/{id}/wallets
│       ├── OperationRoutes.java  POST /deposit, POST /withdraw
│       └── RebalanceRoutes.java  PUT /rebalance/{id}
├── error/                        typed exceptions (Validation / NotFound / Conflict / Unauthorized / Forbidden)
├── model/
│   ├── auth/                     Role, User (account + bcrypt password hash)
│   └── integration/              *** the domain contract + reference implementations ***
├── persistence/
│   ├── Database.java             connection handling + transactions
│   ├── SchemaInitializer.java    idempotent DDL (all tables + indexes)
│   └── dao/                      Agent / Wallet / Operation / Rebalance / User / Session DAOs
├── service/
│   ├── FloatGuardService.java    orchestrates domain + persistence for floats
│   ├── AuthService.java          registration, login, opaque bearer-token sessions
│   └── ServiceResults.java       service-layer return types
└── util/Json.java                Jackson read/write helpers

# Frontend lives at the project root and is copied into the jar under /public
index.html    sign in / register page
agent.html    agent dashboard
admin.html    administrator console
styles.css    shared styles (dark-red gradient theme)
auth.js       shared auth client + authenticated fetch wrapper (loaded by every page)
login.js      login / register page logic
app.js        agent dashboard logic
admin.js      administrator console logic
```

---

## 4. How it works

### Layering rule

```
browser  ->  api/  ->  service/  ->  model/integration/  (+ persistence/dao/)
```

- `api/` only **validates input**, **calls a service**, and **maps results/exceptions to JSON**.
  It never touches a DAO and never contains business rules.
- `service/` is the only place that touches both the domain objects and the database. It is also
  where transaction boundaries live.
- `model/integration/` owns the business rules (non-negative balance, rebalance threshold,
  runway/warning). The REST layer always goes through the domain — it never mutates a balance
  directly.

### Boot order ([Main.java](src/main/java/com/floatguard/Main.java))

1. Read configuration and ensure the storage directory exists.
2. Initialise the schema (idempotent).
3. Build the service graph (`FloatGuardService` + `AuthService`) with stateless DAOs.
4. Register exception mapping, the auth guard and all routes, then start listening.
5. Install a shutdown hook for a graceful stop.

### Request lifecycle

1. Javalin runs the global `before` handler ([AuthMiddleware.java](src/main/java/com/floatguard/api/AuthMiddleware.java)).
   If the path is protected it resolves the `Authorization: Bearer <token>` header to a `User`
   (or rejects with 401/403) and stashes the user on the context.
2. The route handler parses the JSON body, validates it, and calls the relevant service.
3. The service runs its work inside `Database.inTransaction(...)` so the balance change and its
   ledger row are written atomically, delegating the actual decisions to the domain objects.
4. The handler maps the result to a response record and writes it as JSON.
5. Any exception bubbles up to [ErrorMapper.java](src/main/java/com/floatguard/api/ErrorMapper.java),
   which converts it to the standard error envelope.

### Business rules (in the domain, never in `api/`)

1. **Non-negative balance** — enforced only in `FloatWallet.applyOperation(...)`, which throws
   `InsufficientFloatException` (mapped to HTTP 409).
2. **Rebalance threshold** — `FloatWallet.REBALANCE_THRESHOLD` / `isBelowRebalanceThreshold()`
   decide when a rebalance request is raised. The current value `200.0` is a placeholder.
3. **Runway + warning** — `RunwayEstimator` (thresholds `CRITICAL_DAYS = 1.0`,
   `WARNING_DAYS = 3.0` are placeholders).
4. **Rebalance state transition** — `RebalanceRequest.complete()` (idempotent).

---

## 5. Authentication & authorisation

Account handling is kept out of `FloatGuardService` in a dedicated
[AuthService.java](src/main/java/com/floatguard/service/AuthService.java).

- **Passwords** are stored only as bcrypt hashes (`BCrypt.hashpw`).
- **Tokens** are opaque — 32 random bytes, Base64-url encoded — stored in the `sessions` table.
  There is no JWT and no expiry; a session ends when its row is deleted (`POST /auth/logout`).
- The client keeps the token in `localStorage` and sends it as `Authorization: Bearer <token>`.

`AuthMiddleware` protects only a fixed set of API path prefixes, so static assets (the pages,
CSS, JS) are always served. The rules it enforces:

| Path | Requirement |
|---|---|
| `/admin/**` | ADMIN only |
| `POST /agents` | ADMIN only (only an admin may register an agent directly) |
| `/agents/{id}/**` | ADMIN, or the agent that owns `{id}` |
| `/auth/me`, `/auth/logout`, `/deposit`, `/withdraw`, `/rebalance/{id}` | any authenticated user |
| everything else (the HTML/CSS/JS, `POST /auth/register`, `POST /auth/login`) | public |

Additionally, wallet-scoped operations (`/deposit`, `/withdraw`) check ownership: an AGENT may
only act on a wallet belonging to its own agent (see `requireWalletAccess` in
[OperationRoutes.java](src/main/java/com/floatguard/api/routes/OperationRoutes.java)).

---

## 6. REST API

All request and response bodies are JSON. Base URL is `http://localhost:7000`. Protected
endpoints need the `Authorization: Bearer <token>` header.

| # | Method | Path | Access | Purpose | Success |
|---|---|---|---|---|---|
| 1 | `POST` | `/auth/register` | public | Create an ADMIN (invite code) or AGENT account | 201 |
| 2 | `POST` | `/auth/login` | public | Exchange credentials for a bearer token | 200 |
| 3 | `POST` | `/auth/logout` | any | Revoke the current token | 200 |
| 4 | `GET` | `/auth/me` | any | The signed-in account | 200 |
| 5 | `POST` | `/agents` | ADMIN | Register a new agent (no wallets yet) | 201 |
| 6 | `POST` | `/agents/{id}/wallets` | ADMIN / owner | Create a wallet (CASH or EFLOAT) | 201 |
| 7 | `POST` | `/deposit` | any (own wallets) | Log a customer deposit | 200 |
| 8 | `POST` | `/withdraw` | any (own wallets) | Log a customer withdrawal | 200 |
| 9 | `GET` | `/agents/{id}/runway` | ADMIN / owner | Balances + runway estimates + warning | 200 |
| 10 | `GET` | `/agents/{id}/rebalances` | ADMIN / owner | Floats vs threshold + the agent's requests | 200 |
| 11 | `POST` | `/agents/{id}/rebalances` | ADMIN / owner | Raise a PENDING request for one float | 201 |
| 12 | `PUT` | `/rebalance/{id}` | any | Mark a rebalance request COMPLETED | 200 |
| 13 | `GET` | `/admin/agents` | ADMIN | Every agent with balances + warnings | 200 |
| 14 | `POST` | `/admin/agents/{id}/assign-float` | ADMIN | Top up one of an agent's wallets | 200 |

### Status codes

| Status | When | Body `error` code |
|---|---|---|
| `400` | Missing/blank field, wrong type, non-positive amount, password < 6 chars, malformed JSON | `VALIDATION_ERROR` (with per-field `details`) / `INVALID_ARGUMENT` |
| `401` | No token, or an unknown/revoked token, or bad login credentials | `UNAUTHORIZED` |
| `403` | Wrong role, not your account, or an invalid admin invite code | `FORBIDDEN` |
| `404` | Unknown agent / wallet / rebalance id, or an unmatched route | `NOT_FOUND` |
| `409` | Duplicate username or wallet type for an agent | `CONFLICT` |
| `409` | Operation would push a float below zero | `INSUFFICIENT_FLOAT` |
| `500` | Unexpected error (logged with stack trace; generic body) | `INTERNAL_ERROR` |

Every error uses the same envelope:

```json
{
  "error": "NOT_FOUND",
  "message": "Unknown agent 'abc'.",
  "details": {},
  "path": "/agents/abc/runway",
  "timestamp": "2026-11-01T16:18:11.263143Z"
}
```

### 1–4) Authentication

```json
// POST /auth/register  (AGENT — creates the agent record AND both wallets at zero balance)
{ "username": "chuma", "password": "secret123", "role": "AGENT",
  "agentName": "Chuma Mobile Money", "location": "Lusaka, Kamwala" }

// POST /auth/register  (ADMIN — invite code required)
{ "username": "admin", "password": "secret123", "role": "ADMIN",
  "inviteCode": "FLOATGUARD-ADMIN" }

// 201 (same shape for register and login)
{ "token": "hE2s8...", "username": "chuma", "role": "AGENT", "agentId": "5ca6fa22-..." }
```

- `POST /auth/login` takes `{ "username", "password" }` and returns the same body.
- Bad credentials → `401` with a deliberately generic message (no username enumeration).
- Invalid admin invite code → `403`.
- `GET /auth/me` returns the same shape with `token: null`.
- `POST /auth/logout` returns `{ "status": "logged_out" }`.

### 5) `POST /agents` (ADMIN)

```json
// request
{ "name": "Chanda Mobile Money", "location": "Lusaka, Kamwala" }

// 201
{ "id": "5ca6fa22-...", "name": "Chanda Mobile Money", "location": "Lusaka, Kamwala",
  "createdAt": "2026-11-01T16:18:10.753540Z" }
```

Missing `name` or `location` → `400`. Note this creates the agent only; the agent's wallets are
created either by `POST /agents/{id}/wallets` or automatically when someone registers an AGENT
account.

### 6) `POST /agents/{id}/wallets`

```json
// request  (openingBalance is optional, defaults to 0)
{ "type": "CASH", "openingBalance": 1000 }

// 201
{ "id": "dfdc27d6-...", "agentId": "5ca6fa22-...", "type": "CASH",
  "balance": 1000.0, "createdAt": "2026-11-01T16:18:10.920761Z" }
```

`type` accepts `CASH`/`EFLOAT` (plus common aliases such as `cash-float`). Errors: `400` bad type
or negative opening balance, `404` unknown agent, `409` the agent already has a wallet of that type.

### 7–8) `POST /deposit` and `POST /withdraw`

```json
// request
{ "walletId": "dfdc27d6-...", "amount": 500 }

// 200
{ "id": "1ad23b7c-...", "walletId": "dfdc27d6-...", "type": "DEPOSIT", "amount": 500.0,
  "balanceAfter": 1500.0, "balanceBefore": 1000.0,
  "timestamp": "2026-11-01T16:18:11.072759Z", "rebalanceRequestId": null }
```

- `type` is `DEPOSIT` or `WITHDRAWAL`.
- `rebalanceRequestId` is filled in when the operation leaves the float **below the rebalance
  threshold** (see §4) — it is the id to pass to `PUT /rebalance/{id}`.
- Errors: `400` missing/zero/negative amount, `403` someone else's wallet, `404` unknown wallet,
  `409 INSUFFICIENT_FLOAT` when a withdrawal exceeds the balance.

### 9) `GET /agents/{id}/runway`

```json
{
  "agentId": "5ca6fa22-...",
  "agentName": "Chanda Mobile Money",
  "location": "Lusaka, Kamwala",
  "wallets": [
    { "walletId": "dfdc27d6-...", "type": "CASH",   "balance": 100.0, "runwayDays": 0.1,  "warning": "CRITICAL" },
    { "walletId": "162a9e3f-...", "type": "EFLOAT", "balance": 500.0, "runwayDays": null, "warning": "OK" }
  ],
  "overallWarning": "CRITICAL",
  "generatedAt": "2026-11-01T16:18:11.263143Z"
}
```

`runwayDays: null` means no net outflow has been observed (nothing to extrapolate).
`overallWarning` is the most severe warning across the agent's wallets. Unknown agent → `404`.

### 10–11) `GET` / `POST /agents/{id}/rebalances`

```json
// GET 200
{
  "agentId": "5ca6fa22-...",
  "threshold": 200.0,
  "wallets": [
    { "type": "CASH",   "balance": 100.0, "belowThreshold": true },
    { "type": "EFLOAT", "balance": 500.0, "belowThreshold": false }
  ],
  "requests": [
    { "id": "056fab8b-...", "type": "CASH", "status": "PENDING",
      "createdAt": "2026-11-01T16:18:11.098876Z", "completedAt": null }
  ]
}

// POST request (raise a request for one float)
{ "type": "CASH" }
// POST 201 -> a single request object, same shape as one entry in "requests"
```

A `POST` is idempotent per wallet: if a PENDING request already exists it is returned rather than
duplicated.

### 12) `PUT /rebalance/{id}`

```json
// 200
{ "id": "056fab8b-...", "walletId": "dfdc27d6-...", "status": "COMPLETED",
  "createdAt": "2026-11-01T16:18:11.098876Z", "completedAt": "2026-11-01T16:18:11.292643Z" }
```

Idempotent — completing an already-completed request returns 200 with the **original**
`completedAt`. Unknown id → `404`.

### 13) `GET /admin/agents`

```json
// 200
{
  "agents": [
    {
      "id": "5ca6fa22-...",
      "name": "Chanda Mobile Money",
      "location": "Lusaka, Kamwala",
      "wallets": [
        { "walletId": "dfdc27d6-...", "type": "CASH",   "balance": 100.0, "runwayDays": 0.1, "warning": "CRITICAL" },
        { "walletId": "162a9e3f-...", "type": "EFLOAT", "balance": 500.0, "runwayDays": null, "warning": "OK" }
      ],
      "overallWarning": "CRITICAL"
    }
  ]
}
```

### 14) `POST /admin/agents/{id}/assign-float`

```json
// request
{ "walletType": "CASH", "amount": 500 }

// 200
{ "agentId": "5ca6fa22-...", "walletId": "dfdc27d6-...", "walletType": "CASH",
  "amount": 500.0, "balanceAfter": 1500.0 }
```

Assigning float is recorded as a **deposit** against the wallet, so the change is auditable and
the runway estimate reflects the top-up.

---

## 7. Frontend

The UI is three static pages, all loaded behind [auth.js](auth.js):

| Page | Scripts | Endpoints used |
|---|---|---|
| `index.html` | `auth.js`, `login.js` | `POST /auth/register`, `POST /auth/login` |
| `agent.html` | `auth.js`, `app.js` | `/agents/{id}/runway`, `/agents/{id}/rebalances`, `/deposit`, `/withdraw`, `/rebalance/{id}` |
| `admin.html` | `auth.js`, `admin.js` | `GET /admin/agents`, `POST /admin/agents/{id}/assign-float` |

- **`auth.js`** exposes an `Auth` object: it stores the token + user in `localStorage`, provides
  an authenticated `api(method, path, body)` fetch wrapper (which redirects to sign-in on a 401),
  `logout()`, and `guard(role)` — a page guard that sends unauthenticated users to `index.html`
  and redirects users who are on the wrong role's page.
- **`app.js`** (agent) loads the signed-in agent's wallets from the runway endpoint, records
  deposits/withdrawals, and shows rebalance requests. There is **no operation-history endpoint**,
  so the transaction list is kept in the browser (`localStorage`, keyed per agent) and can be
  exported to CSV.
- **`admin.js`** lists every agent with per-wallet balances and warnings, supports search, and
  provides an "Assign float" modal.

The `Agent ID` shown in the UI is a display-only label derived from the account name (initial +
stable hash), not the raw UUID.

---

## 8. Persistence

SQLite, plain JDBC. The schema is created on startup
([SchemaInitializer.java](src/main/java/com/floatguard/persistence/SchemaInitializer.java)):

| Table | Notes |
|---|---|
| `agents` | `id` (UUID, PK), `name`, `location`, `created_at` |
| `wallets` | PK `id`, FK `agent_id`, `type` (`CASH`/`EFLOAT`), `balance`, `UNIQUE(agent_id, type)` |
| `operations` | PK `id`, FK `wallet_id`, `type` (`DEPOSIT`/`WITHDRAWAL`), `amount > 0`, `balance_after`, `occurred_at` |
| `rebalance_requests` | PK `id`, FK `wallet_id`, `status` (`PENDING`/`COMPLETED`), `created_at`, `completed_at` |
| `users` | PK `id`, `username` (UNIQUE), `password_hash`, `role` (`ADMIN`/`AGENT`), FK `agent_id`, `created_at` |
| `sessions` | PK `token`, FK `user_id`, `created_at` |

- Connections apply `PRAGMA foreign_keys = ON` and `busy_timeout = 5000`; the database uses
  `journal_mode = WAL`.
- Multi-row writes run in a single transaction (`Database.inTransaction`). Business
  exceptions (NotFound / Conflict / InsufficientFloat) are **re-thrown unchanged** so they keep
  their HTTP status; only `SQLException`s are wrapped.
- The `UNIQUE` / `CHECK` constraints are safety nets; the primary enforcement lives in the
  domain/service layer.

---

## 9. Connecting to the domain team's code  ← read this if you are integrating

### Ownership boundary

The domain classes are owned by **other team members**. This repo ships **reference
implementations** in `com.floatguard.model.integration` so the application builds and runs end to
end. Replace them with the team's versions when they are ready — as long as the signatures below
hold, **nothing else in the codebase needs to change**.

| Type | Owner | Used by |
|---|---|---|
| `Agent`, `FloatWallet`, `CashFloat`, `EFloat` | domain team | `FloatGuardService`, `AuthService`, `WalletDao` |
| `Operation`, `CustomerDeposit`, `CustomerWithdrawal` | domain team | `FloatGuardService`, `OperationDao`, routes |
| `RebalanceRequest`, `RebalanceStatus` | domain team | `FloatGuardService`, `RebalanceDao` |
| `WalletType` | domain team | routes, DAOs, schema CHECK constraint |
| `RunwayEstimator`, `RunwayEstimate` | domain team | `FloatGuardService.getRunway` |
| `InsufficientFloatException` | domain team | `ErrorMapper` (maps to 409) |

### Required members (the contract)

| Type | Members the rest of the code calls |
|---|---|
| `Agent` | `new Agent(String id, String name, String location)`, `getId/getName/getLocation` |
| `WalletType` (enum) | constants `CASH`, `EFLOAT`; `static WalletType fromString(String)` |
| `FloatWallet` (abstract) | `REBALANCE_THRESHOLD`; `static FloatWallet create(WalletType, String id, String agentId, double balance)`; `getId/getAgentId/getType/getBalance`; `void applyOperation(Operation)`; `boolean isBelowRebalanceThreshold()`; `RebalanceRequest createRebalanceRequest()` |
| `CashFloat` / `EFloat` | extend `FloatWallet`; `getType()` returns `CASH` / `EFLOAT` |
| `Operation` (interface) | `double getAmount()`; `boolean isDeposit()`; `Instant getTimestamp()`; `default double signedAmount()`; `default String getOperationType()` |
| `CustomerDeposit` / `CustomerWithdrawal` | `new (double amount, Instant timestamp)`; must reject `amount <= 0` |
| `RebalanceRequest` | `new (id, walletId, RebalanceStatus, Instant createdAt)` (+ a full constructor with `completedAt` used when rehydrating); `getId/getWalletId/getStatus/getCreatedAt/getCompletedAt`; `void complete()` (idempotent) |
| `RebalanceStatus` (enum) | `PENDING`, `COMPLETED` |
| `InsufficientFloatException` | unchecked; thrown by `applyOperation` |
| `RunwayEstimator` | `static RunwayEstimate estimate(FloatWallet, List<Operation>)`; `static String overallWarning(List<RunwayEstimate>)` |
| `RunwayEstimate` (record) | `walletId, walletType, balance, runwayDays, warning` |

### Steps to swap in the team's classes

1. Put their sources in `src/main/java/com/floatguard/model/integration` (or add the module as a
   Maven dependency in `pom.xml`, then update the imports in `FloatGuardService`, the DAOs,
   `ErrorMapper` and the route classes).
2. Keep every signature in the table above. If a signature must change, the touch points are
   limited to: `service/FloatGuardService.java`, `persistence/dao/*.java`, `api/routes/*.java`,
   `api/ErrorMapper.java`.
3. Keep the enum **names** (`CASH`, `EFLOAT`, `DEPOSIT`, `WITHDRAWAL`, `PENDING`, `COMPLETED`)
   stable — they are written to the database and read back verbatim.
4. Rebuild and re-run: `mvn clean package && java -jar target/floatguard.jar`.

---

## 10. Manual smoke test

With the server running:

```bash
# 1. Create an admin (invite code defaults to FLOATGUARD-ADMIN)
curl -s -X POST localhost:7000/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123","role":"ADMIN","inviteCode":"FLOATGUARD-ADMIN"}'
# -> copy the "token"

TOKEN=<ADMIN_TOKEN>

# 2. Register an agent (creates the agent + both wallets at zero)
curl -s -X POST localhost:7000/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"chuma","password":"secret123","role":"AGENT","agentName":"Chuma Mobile Money","location":"Lusaka, Kamwala"}'
# -> copy the "agentId"

# 3. Assign float as the admin
curl -s -X POST localhost:7000/admin/agents/<AGENT_ID>/assign-float \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"walletType":"CASH","amount":1000}'

# 4. Log in as the agent and record a withdrawal
curl -s -X POST localhost:7000/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"chuma","password":"secret123"}'
# -> copy the agent token; then:
curl -s -X POST localhost:7000/withdraw -H "Authorization: Bearer <AGENT_TOKEN>" \
  -H 'Content-Type: application/json' -d '{"walletId":"<WALLET_ID>","amount":900}'
# (a withdrawal that drops the balance below 200 returns a "rebalanceRequestId")

# 5. View the runway
curl -s localhost:7000/agents/<AGENT_ID>/runway -H "Authorization: Bearer <AGENT_TOKEN>"
```

To complete a rebalance: take the `rebalanceRequestId` from step 4 and call
`PUT /rebalance/<id>`.

---

## 11. Useful notes

- Data persists across restarts by design; delete `data/floatguard.db` (or point
  `FLOATGUARD_DB_URL` elsewhere) for a clean slate.
- Tokens never expire on their own — signing out (`POST /auth/logout`) deletes the session. To
  force everyone out during development, clear the `sessions` table.
- Every agent ends up with exactly two wallets (`UNIQUE(agent_id, type)`); an AGENT account gets
  both created at registration, an admin-created agent starts with none until wallets are added.
- There is no endpoint that lists a session's operation history — the UI keeps its transaction
  log in the browser. Rebalance requests **are** persisted and can be listed per agent.
