# FloatGuard

**FloatGuard: Mobile Money Agent Float Tracker** — an OOP group project for
Zambia University College of Technology.

FloatGuard is a web app that lets mobile-money agents track the two floats they
manage — physical **CASH** and electronic **EFLOAT** — record customer
transactions against them, and request a float top-up ("rebalance") from an
administrator. One Java process serves both the REST API and three HTML pages
(sign-in, agent dashboard, administrator console).

Balances are entered and updated **inside FloatGuard only**. The app does **not**
connect to a mobile-money provider and never moves real money — it is a
record-keeping and monitoring tool.

## Team

OOP Group Project — Zambia University College of Technology.

| No. | Full name | Student number |
|---|---|---|
| 1 | Vidson Mwangosy | 2420932 |
| 2 | Mathews Mulemba | 2420914 |
| 3 | Nyangoloma Peter | 2420929 |
| 4 | Robert Muntanga | 2420908 |
| 5 | Wongani Miyanza | 2420907 |

## What it does

| Page | Who uses it | Purpose |
|---|---|---|
| `/index.html` | everyone | sign in / create an account |
| `/agent.html` | AGENT | record deposits & withdrawals, watch runway, request / cancel rebalances |
| `/admin.html` | ADMIN (a "super agent") | monitor agents in their location, assign float, approve/pass/delete rebalance requests |

- Every agent owns exactly two wallets: **CASH** and **EFLOAT**.
- The dashboard shows cash, e-float, combined balance, low-float warnings, and an
  estimated "runway" (days until the float runs out).
- Customer transactions are recorded as **paired** movements:
  - **Customer cash-in** (a deposit): CASH increases and EFLOAT decreases.
  - **Customer cash-out** (a withdrawal): CASH decreases and EFLOAT increases.
- Both balances are checked before anything is written. If either side would go
  negative, the whole transaction is rejected and no balance or ledger row changes.
- Agents request a cash **or** e-float top-up. A request is only accepted when the
  agent already holds enough of the **opposite** float to pay for it, so an
  unaffordable request is never raised. Administrators then **approve** (the float
  is moved and paid for with the opposite float), **pass** the request to another
  administrator in the same location, or **delete** it.
- While a wallet has a pending request, the agent can **cancel** it from the
  dashboard to free the wallet and raise a new one.
- Administrators can also top up an agent directly with **Assign float**.

State (accounts, wallets, ledger, requests, sessions) lives in SQLite. The agent
dashboard's transaction history is additionally kept in that browser's
`localStorage` (there is no server-side history endpoint).

## Roles and the "super agent" concept

There are two account roles, defined by the `Role` enum:

- **AGENT** — tied to exactly one agent record and its two wallets.
- **ADMIN** — an administrator account.

Separately, every agent record has a `kind` of either `AGENT` or `SUPER_AGENT`
(a business profile, not a login role):

- Registering an **AGENT** account creates an agent record with `kind = AGENT` and
  both wallets (using the opening balances you supply).
- Registering an **ADMIN** account creates an agent record with
  `kind = SUPER_AGENT` — the **super agent** — plus its own two wallets.

A super agent can only manage agents and rebalance requests **in its own
registered location** (matched case-insensitively). A super agent's own wallets
are the source of float for `assign-float` and rebalance approvals, so a
super agent needs float on hand before it can help an agent.

## Requirements

Anyone who clones this project must install a JDK before it will build:

| Concern | Choice |
|---|---|
| Language | Java 17 (builds with any JDK ≥ 17) |
| HTTP server | Javalin 6.3.0 (Jetty 11) |
| Persistence | SQLite via `org.xerial:sqlite-jdbc` (plain JDBC, no ORM) |
| JSON | Jackson (`jackson-databind`) |
| Auth | `jbcrypt` password hashing + opaque `SecureRandom` bearer tokens |
| Logging | slf4j-simple |
| Build | Maven (fat jar via `maven-shade-plugin`) |

- **JDK 17 or newer** — verify with `java -version`.
- **Maven** — you do **not** need to install it separately: the bundled Maven
  Wrapper (`mvnw` / `mvnw.cmd`) downloads Apache Maven 3.10.0 on first run. If you
  prefer a system Maven, install 3.9+ and verify with `mvn -v`.

The frontend is deliberately vanilla HTML/CSS/JS — no frameworks, no Node build
step, no external dependencies. Maven packages the pages into the jar.

## Run the application

From the project folder (the one containing `pom.xml`):

macOS / Linux:

```bash
./mvnw clean package
java -jar target/floatguard.jar
```

Windows (PowerShell / cmd):

```powershell
.\mvnw.cmd clean package
java -jar target\floatguard.jar
```

Then open <http://localhost:7000/>.

- `mvnw clean package` compiles the Java code and packages the frontend into
  `target/floatguard.jar`. The wrapper downloads Maven the first time, so only a
  JDK is required.
- Use **forward slashes** (`target/floatguard.jar`) on macOS/Linux; a backslash is
  an escape character in the shell and collapses the path.
- `mvn`/`mvnw` must be invoked from the folder that contains `pom.xml`.
- Keep the terminal open while using the app; press `Ctrl+C` to stop the server.

If you have a system Maven on your `PATH`, you may use `mvn clean package`
instead of the wrapper. (In this project's development environment Maven was not
on `PATH`; a portable Maven 3.9.9 was used via
`~/.local/opt/apache-maven-3.9.9/bin/mvn`.)

The database directory (`data/`) and the schema are created automatically on
first start.

### Quick start

1. Open <http://localhost:7000/> and switch to **Create account**.
2. Register an **Administrator** using an invite code (default `FL12` — see
   configuration below). Admin registration also creates the super-agent profile
   and its two wallets, so fill in the business name, location, NRC, phone, and
   opening balances too.
3. Sign out, then register an **Agent** (business name, location, NRC, phone, and
   opening cash/e-float balances). The agent record and both wallets are created
   automatically.
4. Sign in as the administrator. Use **Assign float** to move float from the
   super agent's own wallet into the agent's wallet, and review any rebalance
   requests.
5. Sign in as the agent to record deposits/withdrawals and watch the runway
   warnings.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `7000` | HTTP port to listen on |
| `FLOATGUARD_DB_URL` | `jdbc:sqlite:data/floatguard.db` | JDBC URL of the SQLite database |
| `FLOATGUARD_ADMIN_INVITE_CODE` | `FL12` | Code(s) required to self-register an ADMIN account. Comma-separated; codes are upper-cased |

```bash
PORT=8080 FLOATGUARD_DB_URL="jdbc:sqlite:/tmp/floatguard.db" \
  FLOATGUARD_ADMIN_INVITE_CODE="my-secret-code" \
  java -jar target/floatguard.jar
```

The database path is relative to the directory Java is started from, and the
parent directory is created if missing. Choose a private invite code before
sharing the app with anyone else.

## Project structure

```
src/main/java/com/floatguard/
├── Main.java                     entry point: config -> schema -> services -> routes -> listen
├── config/AppConfig.java         reads PORT / FLOATGUARD_DB_URL / FLOATGUARD_ADMIN_INVITE_CODE
├── api/                          REST layer — validate, delegate, map to JSON (NO business rules)
│   ├── ApiRoutes.java            registers error mapping + auth guard + all route groups
│   ├── AuthMiddleware.java       bearer-token authentication + role/ownership/location guard
│   ├── ErrorMapper.java          exception -> HTTP status translation
│   ├── Responses.java            JSON response / error-envelope helpers
│   ├── Validation.java           input validation helpers
│   ├── dto/                      request/response records (the wire shapes)
│   └── routes/                   one handler per resource group
│       ├── AuthRoutes.java       /auth/register, /auth/login, /auth/logout, /auth/me
│       ├── AgentRoutes.java      POST /agents, runway + rebalance read/write/cancel
│       ├── WalletRoutes.java     POST /agents/{id}/wallets
│       ├── OperationRoutes.java  POST /deposit, POST /withdraw
│       ├── AdminRoutes.java      /admin/profile, /admin/agents, assign-float, request list
│       └── RebalanceRoutes.java  /admin/rebalance-requests/{id}/approve, /pass, DELETE
├── error/                        typed exceptions (Validation / NotFound / Conflict / Unauthorized / Forbidden)
├── model/
│   ├── auth/                     Role (ADMIN/AGENT), User (account + bcrypt password hash)
│   └── integration/              the domain contract + reference implementations
├── persistence/
│   ├── Database.java             connection handling + transactions
│   ├── SchemaInitializer.java    idempotent DDL (all tables + indexes + additive migrations)
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
styles.css    shared styles (burgundy-red gradient theme)
auth.js       shared auth client + authenticated fetch wrapper (loaded by every page)
login.js      login / register page logic
app.js        agent dashboard logic
admin.js      administrator console logic
```

> `RebalanceRequest.java` in the repository root is an unused legacy copy of the
> domain class; it is **not** compiled (the compiled version lives under
> `model/integration/`) and can be ignored.

## How it works

### Layering rule

```
browser  ->  api/  ->  service/  ->  model/integration/  (+ persistence/dao/)
```

- `api/` only **validates input**, **calls a service**, and **maps
  results/exceptions to JSON**. It never touches a DAO and never contains
  business rules.
- `service/` is the only place that touches both the domain objects and the
  database. Transaction boundaries live here.
- `model/integration/` owns the business rules (non-negative balance, rebalance
  threshold, runway/warning). The REST layer always goes through the domain — it
  never mutates a balance directly.

### Boot order ([Main.java](src/main/java/com/floatguard/Main.java))

1. Read configuration and ensure the storage directory exists.
2. Initialise the schema (idempotent).
3. Build the service graph (`FloatGuardService` + `AuthService`) with stateless DAOs.
4. Register exception mapping, the auth guard and all routes, then start listening.
5. Install a shutdown hook for a graceful stop.

### Request lifecycle

1. Javalin runs the global `before` handler
   ([AuthMiddleware.java](src/main/java/com/floatguard/api/AuthMiddleware.java)).
   On a protected path it resolves `Authorization: Bearer <token>` to a `User`
   (rejecting with 401/403 otherwise) and stashes the user on the context.
2. The route handler parses the JSON body, validates it, and calls the relevant
   service.
3. The service runs its work inside `Database.inTransaction(...)` so the balance
   change and its ledger row(s) are written atomically, delegating the decisions
   to the domain objects.
4. The handler maps the result to a response record and writes it as JSON.
5. Any exception bubbles up to
   [ErrorMapper.java](src/main/java/com/floatguard/api/ErrorMapper.java), which
   converts it to the standard error envelope.

### Business rules (in the domain, never in `api/`)

1. **Non-negative balance** — enforced only in `FloatWallet.applyOperation(...)`,
   which throws `InsufficientFloatException` (mapped to HTTP 409).
2. **Paired customer transactions** — a deposit to CASH is recorded as a
   withdrawal from EFLOAT and vice-versa
   (`FloatGuardService.logCustomerTransaction`).
3. **Rebalance threshold** — `FloatWallet.REBALANCE_THRESHOLD` /
   `isBelowRebalanceThreshold()` decide when a float is flagged. The current value
   `200.0` is a placeholder.
4. **Runway + warning** — `RunwayEstimator`. Runway = balance ÷ average daily net
   outflow; `runwayDays` is `null` when there is no net outflow. Thresholds
   `CRITICAL_DAYS = 1.0` and `WARNING_DAYS = 3.0` are placeholders.
5. **Rebalance state transition** — `RebalanceRequest.complete()` (idempotent).

## Authentication & authorisation

Account handling is kept out of `FloatGuardService` in a dedicated
[AuthService.java](src/main/java/com/floatguard/service/AuthService.java).

- **Passwords** are stored only as bcrypt hashes (`BCrypt.hashpw`); registration
  requires at least 6 characters.
- **Tokens** are opaque — 32 random bytes, Base64-url encoded — stored in the
  `sessions` table. There is no JWT and no expiry; a session ends when its row is
  deleted (`POST /auth/logout`).
- The client keeps the token in `localStorage` (`floatguard_token_v1`) and sends
  it as `Authorization: Bearer <token>`.

`AuthMiddleware` protects only a fixed set of API path prefixes, so static assets
(the pages, CSS, JS) are always served. The rules it enforces:

| Path | Requirement |
|---|---|
| `/admin`, `/admin/**` | `ADMIN` role **and** the account has a super-agent profile (`agentId != null`) |
| `POST /agents` | `ADMIN` only |
| `/agents/{id}/**` | `ADMIN` managing an agent **in the same location**, or the agent that owns `{id}` |
| `/auth/me`, `/auth/logout`, `/deposit`, `/withdraw` | any authenticated user |
| everything else (HTML/CSS/JS, `POST /auth/register`, `POST /auth/login`) | public |

Additionally:

- `POST /agents` and `POST /agents/{id}/wallets` require the caller's super agent
  and the target agent to share a location (`requireSameLocation`, HTTP 403
  otherwise).
- `/deposit` and `/withdraw` check ownership: a user may only act on a wallet that
  belongs to their own agent record (`requireWalletAccess` in
  [OperationRoutes.java](src/main/java/com/floatguard/api/routes/OperationRoutes.java)).

## REST API

All request and response bodies are JSON. Base URL is `http://localhost:7000`.
Protected endpoints need the `Authorization: Bearer <token>` header. "Same
location" means the caller's super agent and the target agent have matching
locations.

| # | Method | Path | Access | Purpose | Success |
|---|---|---|---|---|---|
| 1 | `POST` | `/auth/register` | public | Create an ADMIN (invite code) or AGENT account (+ profile + wallets) | 201 |
| 2 | `POST` | `/auth/login` | public | Exchange credentials for a bearer token | 200 |
| 3 | `POST` | `/auth/logout` | any | Revoke the current token | 200 |
| 4 | `GET` | `/auth/me` | any | The signed-in account | 200 |
| 5 | `POST` | `/agents` | ADMIN (same location) | Register a new agent (no wallets yet) | 201 |
| 6 | `POST` | `/agents/{id}/wallets` | ADMIN (same location) / owner | Create a wallet (CASH or EFLOAT) | 201 |
| 7 | `GET` | `/agents/{id}/runway` | ADMIN (same location) / owner | Balances + runway estimates + warning | 200 |
| 8 | `GET` | `/agents/{id}/rebalances` | ADMIN (same location) / owner | Floats vs threshold + the agent's requests | 200 |
| 9 | `POST` | `/agents/{id}/rebalances` | ADMIN (same location) / owner | Raise a PENDING request for one float | 201 |
| 10 | `DELETE` | `/agents/{id}/rebalances/{requestId}` | ADMIN (same location) / owner | Cancel a PENDING request so the wallet is freed | 200 |
| 11 | `POST` | `/deposit` | any (own wallets) | Log a customer cash-in | 200 |
| 12 | `POST` | `/withdraw` | any (own wallets) | Log a customer cash-out | 200 |
| 13 | `GET` | `/admin/profile` | ADMIN | The super agent's own profile + wallets | 200 |
| 14 | `GET` | `/admin/agents` | ADMIN | Agents in the super agent's location, with balances + warnings | 200 |
| 15 | `GET` | `/admin/rebalance-requests` | ADMIN | Pending requests in the location (excluding ones this admin passed) | 200 |
| 16 | `PUT` | `/admin/rebalance-requests/{id}/approve` | ADMIN | Approve a request: move float + take opposite-float payment | 200 |
| 17 | `PUT` | `/admin/rebalance-requests/{id}/pass` | ADMIN | Hand the request to another local super agent | 200 |
| 18 | `DELETE` | `/admin/rebalance-requests/{id}` | ADMIN | Delete a request (clear a stuck or unapprovable one) | 200 |
| 19 | `POST` | `/admin/agents/{id}/assign-float` | ADMIN (same location) | Move float from the admin's wallet to an agent's wallet | 200 |

### Error statuses

| Status | When | `error` code |
|---|---|---|
| `400` | Missing/blank field, wrong type, non-positive amount, password < 6 chars, negative opening balance, malformed JSON | `VALIDATION_ERROR` (with per-field `details`) or `INVALID_ARGUMENT` |
| `401` | No token, unknown/revoked token, or bad login credentials | `UNAUTHORIZED` |
| `403` | Wrong role, not your account, different location, invalid admin invite code | `FORBIDDEN` |
| `404` | Unknown agent / wallet / rebalance id, or an unmatched route | `NOT_FOUND` |
| `409` | Duplicate username, wallet of that type already exists, pending request already exists, request already approved/passed, or cancelling a request that is not pending | `CONFLICT` |
| `409` | An operation would push a float below zero | `INSUFFICIENT_FLOAT` |
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
// POST /auth/register  (AGENT — creates the agent record AND both wallets)
{ "username": "chuma", "password": "secret123", "role": "AGENT",
  "agentName": "Chuma Mobile Money", "location": "Lusaka, Kamwala",
  "nrcNumber": "123456/78/9", "phoneNumber": "0970000000",
  "openingCash": 0, "openingEFloat": 0 }

// POST /auth/register  (ADMIN — invite code required, and profile fields too,
//                        because the super-agent record and its wallets are created)
{ "username": "admin", "password": "secret123", "role": "ADMIN",
  "inviteCode": "FL12",
  "agentName": "Central Hub", "location": "Lusaka, Kamwala",
  "nrcNumber": "987654/32/1", "phoneNumber": "0960000000",
  "openingCash": 5000, "openingEFloat": 5000 }

// 201 (same shape for register and login)
{ "token": "hE2s8...", "username": "chuma", "role": "AGENT", "agentId": "5ca6fa22-..." }
```

- Both roles must supply `agentName`, `location`, `nrcNumber`, `phoneNumber`,
  `openingCash` and `openingEFloat`; only `inviteCode` is ADMIN-specific.
- `POST /auth/login` takes `{ "username", "password" }` and returns the same body.
- Bad credentials → `401` with a deliberately generic message (no username
  enumeration). Invalid admin invite code → `403`. Duplicate username → `409`.
- `GET /auth/me` returns the same shape with `token: null`.
- `POST /auth/logout` returns `{ "status": "logged_out" }`.

### 5) `POST /agents` (ADMIN)

```json
// request (creates the agent record only — no wallets)
{ "name": "Chanda Mobile Money", "location": "Lusaka, Kamwala",
  "nrcNumber": "111222/33/4", "phoneNumber": "0955555555" }

// 201
{ "id": "dfdc27d6-...", "name": "Chanda Mobile Money", "location": "Lusaka, Kamwala",
  "phoneNumber": "0955555555", "createdAt": "2026-11-01T16:18:10.920761Z" }
```

The target `location` must match the calling super agent's location, otherwise
`403`. Missing `name`/`location`/`nrcNumber`/`phoneNumber` → `400`. Wallets are
added later with `POST /agents/{id}/wallets`, or automatically when someone
registers an AGENT account.

### 6) `POST /agents/{id}/wallets`

```json
// request (type required; openingBalance optional, defaults to 0)
{ "type": "CASH", "openingBalance": 1000 }

// 201
{ "id": "dfdc27d6-...", "agentId": "5ca6fa22-...", "type": "CASH",
  "balance": 1000.0, "createdAt": "2026-11-01T16:18:10.920761Z" }
```

`type` accepts `CASH`/`EFLOAT` (plus common aliases such as `cash-float`).
Errors: `400` bad type or negative opening balance, `404` unknown agent,
`409` the agent already has a wallet of that type.

### 7) `GET /agents/{id}/runway`

```json
{
  "agentId": "5ca6fa22-...",
  "agentName": "Chanda Mobile Money",
  "location": "Lusaka, Kamwala",
  "phoneNumber": "0955555555",
  "wallets": [
    { "walletId": "dfdc27d6-...", "type": "CASH",   "balance": 100.0, "runwayDays": 0.1,  "warning": "CRITICAL" },
    { "walletId": "162a9e3f-...", "type": "EFLOAT", "balance": 500.0, "runwayDays": null, "warning": "OK" }
  ],
  "overallWarning": "CRITICAL",
  "generatedAt": "2026-11-01T16:18:11.263143Z"
}
```

### 8–10) `GET` / `POST` / `DELETE /agents/{id}/rebalances`

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
    { "id": "056fab8b-...", "type": "CASH", "amount": 300.0, "area": "Kamwala",
      "status": "PENDING", "handoffCount": 0,
      "createdAt": "2026-11-01T16:18:11.098876Z", "completedAt": null }
  ]
}

// POST request (raise a request for one float; amount and area are required)
{ "type": "CASH", "amount": 300, "area": "Kamwala" }
// POST 201 -> a single request object, same shape as one entry in "requests"

// DELETE /agents/{id}/rebalances/{requestId}  -> 200
{ "id": "056fab8b-...", "status": "DELETED", "message": "Rebalance request cancelled." }
```

A wallet can have at most **one** PENDING request at a time; submitting a second
returns `409`.

**The requested float is paid for with the opposite float.** A cash top-up is
settled in e-float and an e-float top-up in cash, so at request time the agent
must already hold **at least the requested amount of the opposite float**.
If it does not, the request is rejected with `409 INSUFFICIENT_FLOAT`
(`"Your CASH/EFLOAT balance is too low to pay for this ... top-up."`) and no
request is created — this prevents requests that could never be approved from
piling up and locking the wallet.

**Cancelling.** The owning agent (or an administrator in the same location) can
cancel a PENDING request via `DELETE /agents/{id}/rebalances/{requestId}`, which
frees the wallet so a new request can be raised. A `COMPLETED` request cannot be
cancelled this way (`409`).

### 11–12) `POST /deposit` and `POST /withdraw`

```json
// request — walletId MUST be the agent's CASH wallet
{ "walletId": "dfdc27d6-...", "amount": 500 }

// 200
{ "id": "1ad23b7c-...", "walletId": "dfdc27d6-...", "type": "DEPOSIT", "amount": 500.0,
  "balanceAfter": 1500.0, "balanceBefore": 1000.0,
  "timestamp": "2026-11-01T16:18:11.072759Z", "rebalanceRequestId": null,
  "pairedOperation": {
    "id": "9c11...", "walletId": "162a9e3f-...", "type": "EFLOAT",
    "operationType": "WITHDRAWAL", "amount": 500.0,
    "balanceBefore": 2000.0, "balanceAfter": 1500.0
  } }
```

- `type` on the response is the operation type: `DEPOSIT` or `WITHDRAWAL`.
- The e-float side is always applied as the **opposite** movement and returned in
  `pairedOperation`. Both balances and both ledger rows commit in one transaction.
- `rebalanceRequestId` is a **legacy field that is always `null`** — rebalance
  requests are now raised explicitly via `POST /agents/{id}/rebalances`, not
  inferred from a low balance.
- Errors: `400` missing/zero/negative amount, `403` someone else's wallet,
  `404` unknown wallet, `409 INSUFFICIENT_FLOAT` when either side would go below
  zero (no partial update).

### 13) `GET /admin/profile`

```json
// 200 — the signed-in super agent
{ "id": "5ca6fa22-...", "name": "Central Hub", "location": "Lusaka, Kamwala",
  "phoneNumber": "0960000000",
  "wallets": [ { "walletId": "…", "type": "CASH", "balance": 5000.0, "runwayDays": null, "warning": "OK" } ],
  "overallWarning": "OK" }
```

### 14) `GET /admin/agents`

```json
// 200 — ordinary agents in the super agent's location
{
  "agents": [
    {
      "id": "5ca6fa22-...",
      "name": "Chanda Mobile Money",
      "location": "Lusaka, Kamwala",
      "phoneNumber": "0955555555",
      "wallets": [
        { "walletId": "dfdc27d6-...", "type": "CASH",   "balance": 100.0, "runwayDays": 0.1, "warning": "CRITICAL" },
        { "walletId": "162a9e3f-...", "type": "EFLOAT", "balance": 500.0, "runwayDays": null, "warning": "OK" }
      ],
      "overallWarning": "CRITICAL"
    }
  ]
}
```

### 15) `GET /admin/rebalance-requests`

```json
// 200
{
  "requests": [
    {
      "id": "056fab8b-...",
      "agentId": "5ca6fa22-...",
      "agentName": "Chanda Mobile Money",
      "agentPhoneNumber": "0955555555",
      "location": "Lusaka, Kamwala",
      "area": "Kamwala",
      "walletType": "CASH",
      "amount": 300.0,
      "status": "PENDING",
      "handoffCount": 0,
      "createdAt": "2026-11-01T16:18:11.098876Z",
      "completedAt": null
    }
  ]
}
```

Only requests in the super agent's location are returned, and any request this
super agent has already passed is excluded.

### 16) `PUT /admin/rebalance-requests/{id}/approve`

Approving a top-up is a **two-sided trade**, all in one transaction:

- the requested amount moves **super agent → agent** (a deposit to the agent's
  wallet), and
- the payment moves **agent → super agent** in the **opposite** float type
  (an EFLOAT top-up is paid for in cash; a CASH top-up is paid for in e-float).

If the agent lacks enough of the paying float, or the super agent lacks enough of
the requested float, the whole approval rolls back.

```json
// 200
{ "id": "056fab8b-...", "walletId": "dfdc27d6-...", "walletType": "CASH",
  "amount": 300.0, "balanceAfter": 400.0, "sourceBalanceAfter": 4700.0,
  "status": "COMPLETED", "createdAt": "2026-11-01T16:18:11.098876Z",
  "completedAt": "2026-11-01T16:25:00.000000Z" }
```

Errors: `404` unknown id, `403` different location, `409` already approved, or
already passed by this administrator, or the requesting profile is a super agent.

### 17) `PUT /admin/rebalance-requests/{id}/pass`

Records a handoff so another super agent in the same location can pick the request
up. The passing administrator can no longer approve it.

```json
// 200
{ "id": "056fab8b-...", "status": "PENDING",
  "message": "Passed to another super agent in your location." }
```

Errors: `404` unknown id, `403` different location, `409` not pending or already
passed by this administrator.

### 18) `DELETE /admin/rebalance-requests/{id}`

Deletes a request so an administrator can clear one that can no longer be
approved (for example a request that has been passed around and then found to be
unaffordable). The wallet is freed for a new request. Only requests in the super
agent's own location can be deleted.

```json
// 200
{ "id": "056fab8b-...", "status": "DELETED", "message": "Rebalance request deleted." }
```

Errors: `404` unknown id, `403` different location.

### 19) `POST /admin/agents/{id}/assign-float`

Moves float **from the super agent's own wallet** into the agent's wallet (so the
admin must have float of that type available). The agent side is recorded as a
deposit, so the change is auditable and the runway estimate reflects it.

```json
// request
{ "walletType": "CASH", "amount": 500 }

// 200
{ "agentId": "5ca6fa22-...", "walletId": "dfdc27d6-...", "walletType": "CASH",
  "amount": 500.0, "balanceAfter": 1500.0 }
```

Errors: `400` missing/invalid `walletType` or non-positive amount, `404` unknown
agent, `409 INSUFFICIENT_FLOAT` when the super agent's wallet would go negative.

## Frontend

The UI is three static pages, all loaded behind [auth.js](auth.js):

| Page | Scripts | Endpoints used |
|---|---|---|
| `index.html` | `auth.js`, `login.js` | `POST /auth/register`, `POST /auth/login` |
| `agent.html` | `auth.js`, `app.js` | `/agents/{id}/runway`, `/agents/{id}/rebalances` (GET/POST/DELETE), `/deposit`, `/withdraw` |
| `admin.html` | `auth.js`, `admin.js` | `/admin/profile`, `/admin/agents`, `/admin/rebalance-requests`, `…/approve`, `…/pass`, `DELETE …/{id}`, `/admin/agents/{id}/assign-float` |

- **`auth.js`** exposes an `Auth` object: it stores the token + user in
  `localStorage` (`floatguard_token_v1`, `floatguard_user_v1`), provides an
  authenticated `api(method, path, body)` fetch wrapper (redirecting to sign-in on
  a 401), `logout()`, and `guard(role)` — a page guard that sends unauthenticated
  users to `index.html` and users on the wrong role's page to their home page.
  When served from a local Live Server (`localhost:5500`/`5501`) it targets the
  backend on port `7000` automatically.
- **`app.js`** (agent) loads the signed-in agent's wallets from the runway
  endpoint, records deposits/withdrawals, submits rebalance requests, and can
  **cancel** a pending one. There is **no server-side operation-history
  endpoint**, so the transaction list is kept in the browser (`localStorage`,
  keyed per agent) and can be exported to CSV.
- **`admin.js`** shows the super agent's profile, lists the agents in its location
  with per-wallet balances and warnings, supports search, provides the "Assign
  float" modal, and lets it approve, pass or **delete** rebalance requests.

Running the frontend separately: start the backend (`java -jar
target/floatguard.jar`), then serve the root folder with Live Server on
`localhost:5500`/`5501`. CORS is pre-configured for those origins. Do **not** open
`index.html` via `file://` — the API calls will fail.

## Persistence

SQLite, plain JDBC. The schema is created on startup
([SchemaInitializer.java](src/main/java/com/floatguard/persistence/SchemaInitializer.java)):

| Table | Notes |
|---|---|
| `agents` | `id` (UUID, PK), `name`, `location`, `nrc_number`, `phone_number`, `kind` (`AGENT`/`SUPER_AGENT`), `created_at` |
| `wallets` | PK `id`, FK `agent_id`, `type` (`CASH`/`EFLOAT`), `balance`, `UNIQUE(agent_id, type)` |
| `operations` | PK `id`, FK `wallet_id`, `type` (`DEPOSIT`/`WITHDRAWAL`), `amount > 0`, `balance_after`, `occurred_at` |
| `rebalance_requests` | PK `id`, FK `wallet_id`, `requested_amount`, `area`, `status` (`PENDING`/`COMPLETED`), `created_at`, `completed_at` |
| `users` | PK `id`, `username` (UNIQUE), `password_hash`, `role` (`ADMIN`/`AGENT`), FK `agent_id`, `created_at` |
| `sessions` | PK `token`, FK `user_id`, `created_at` |
| `rebalance_request_passes` | PK (`request_id`, `super_agent_user_id`), `passed_at` — the handoff records |

- Indexes exist on `wallets(agent_id)`, `operations(wallet_id)`,
  `operations(wallet_id, occurred_at)`, `rebalance_requests(wallet_id)`,
  `users(agent_id)` and `sessions(user_id)`.
- Connections apply `PRAGMA foreign_keys = ON` and `busy_timeout = 5000`; the
  database uses `journal_mode = WAL`.
- New columns are added with additive, idempotent `ALTER TABLE ... ADD COLUMN`
  migrations (`nrc_number`, `phone_number`, `kind`, `requested_amount`, `area`),
  so an existing database upgrades itself on startup.
- Multi-row writes run in a single transaction (`Database.inTransaction`).
  Business exceptions (NotFound / Conflict / Forbidden / InsufficientFloat) are
  **re-thrown unchanged** so they keep their HTTP status; only `SQLException`s are
  wrapped.
- `UNIQUE` / `CHECK` constraints are safety nets; the primary enforcement lives in
  the domain/service layer.

## Connecting to the domain team's code

The classes in `com.floatguard.model.integration` are owned by **other team
members**. This repo ships **reference implementations** so the application builds
and runs end to end. They can be replaced when the team's versions are ready — as
long as the signatures below hold, **nothing else in the codebase needs to change**.

| Type | Owner | Used by |
|---|---|---|
| `Agent`, `FloatWallet`, `CashFloat`, `EFloat` | domain team | `FloatGuardService`, `AuthService`, `WalletDao` |
| `Operation`, `CustomerDeposit`, `CustomerWithdrawal` | domain team | `FloatGuardService`, `OperationDao`, routes |
| `RebalanceRequest`, `RebalanceStatus` | domain team | `FloatGuardService`, `RebalanceDao` |
| `WalletType` | domain team | routes, DAOs, schema CHECK constraint |
| `RunwayEstimator`, `RunwayEstimate` | domain team | `FloatGuardService` runway methods |
| `InsufficientFloatException` | domain team | `ErrorMapper` (maps to 409) |

### Required members (the contract)

| Type | Members the rest of the code calls |
|---|---|
| `Agent` | `new Agent(String id, String name, String location, String nrcNumber, String phoneNumber, String kind)`; `getId/getName/getLocation/getNrcNumber/getPhoneNumber`; `isSuperAgent()`; constants `AGENT`, `SUPER_AGENT` |
| `WalletType` (enum) | constants `CASH`, `EFLOAT`; `opposite()`; `static WalletType fromString(String)` |
| `FloatWallet` (abstract) | `REBALANCE_THRESHOLD`; `static FloatWallet create(WalletType, String id, String agentId, double balance)`; `getId/getAgentId/getType/getBalance`; `void applyOperation(Operation)`; `boolean isBelowRebalanceThreshold()`; `RebalanceRequest createRebalanceRequest(double amount, String area)` |
| `CashFloat` / `EFloat` | extend `FloatWallet`; `getType()` returns `CASH` / `EFLOAT` |
| `Operation` (interface) | `double getAmount()`; `boolean isDeposit()`; `Instant getTimestamp()`; `default double signedAmount()`; `default String getOperationType()` |
| `CustomerDeposit` / `CustomerWithdrawal` | `new (double amount, Instant timestamp)`; must reject `amount <= 0` |
| `RebalanceRequest` | `new (id, walletId, amount, area, RebalanceStatus, Instant createdAt)` (+ a full constructor with `completedAt`); `getId/getWalletId/getAmount/getArea/getStatus/getCreatedAt/getCompletedAt`; `void complete()` (idempotent) |
| `RebalanceStatus` (enum) | `PENDING`, `COMPLETED` |
| `InsufficientFloatException` | unchecked; thrown by `applyOperation` |
| `RunwayEstimator` | `static RunwayEstimate estimate(FloatWallet, List<Operation>)`; `static String overallWarning(List<RunwayEstimate>)` |
| `RunwayEstimate` (record) | `walletId, walletType, balance, runwayDays, warning` |

### Steps to swap in the team's classes

1. Put their sources in `src/main/java/com/floatguard/model/integration` (or add
   the module as a Maven dependency in `pom.xml`, then update the imports in
   `FloatGuardService`, the DAOs, `ErrorMapper` and the route classes).
2. Keep every signature in the table above. If a signature must change, the touch
   points are limited to: `service/FloatGuardService.java`,
   `service/AuthService.java`, `persistence/dao/*.java`, `api/routes/*.java`,
   `api/ErrorMapper.java`.
3. Keep the enum **names** (`CASH`, `EFLOAT`, `DEPOSIT`, `WITHDRAWAL`, `PENDING`,
   `COMPLETED`) stable — they are written to the database and read back verbatim.
4. Rebuild and re-run: `./mvnw clean package && java -jar target/floatguard.jar`.

## Manual smoke test

With the server running (invite code defaults to `FL12`):

```bash
# 1. Create an admin / super agent
curl -s -X POST localhost:7000/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123","role":"ADMIN","inviteCode":"FL12",
       "agentName":"Central Hub","location":"Lusaka, Kamwala",
       "nrcNumber":"987654/32/1","phoneNumber":"0960000000",
       "openingCash":5000,"openingEFloat":5000}'
# -> copy the "token"

TOKEN=<ADMIN_TOKEN>

# 2. Register an agent (creates the agent + both wallets)
curl -s -X POST localhost:7000/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"chuma","password":"secret123","role":"AGENT",
       "agentName":"Chuma Mobile Money","location":"Lusaka, Kamwala",
       "nrcNumber":"123456/78/9","phoneNumber":"0970000000",
       "openingCash":0,"openingEFloat":0}'
# -> copy the "agentId"

# 3. Assign float as the admin (moves from the admin's own CASH wallet)
curl -s -X POST localhost:7000/admin/agents/<AGENT_ID>/assign-float \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"walletType":"CASH","amount":1000}'

# 4. Log in as the agent and record a withdrawal
curl -s -X POST localhost:7000/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"chuma","password":"secret123"}'
# -> copy the agent token; then:
curl -s -X POST localhost:7000/withdraw -H "Authorization: Bearer <AGENT_TOKEN>" \
  -H 'Content-Type: application/json' -d '{"walletId":"<CASH_WALLET_ID>","amount":900}'

# 5. View the runway
curl -s localhost:7000/agents/<AGENT_ID>/runway -H "Authorization: Bearer <AGENT_TOKEN>"

# 6. Raise a rebalance request as the agent
curl -s -X POST localhost:7000/agents/<AGENT_ID>/rebalances \
  -H "Authorization: Bearer <AGENT_TOKEN>" -H 'Content-Type: application/json' \
  -d '{"type":"CASH","amount":500,"area":"Kamwala"}'

# 7. As the admin: list, then approve (or pass) the request
curl -s localhost:7000/admin/rebalance-requests -H "Authorization: Bearer $TOKEN"
curl -s -X PUT localhost:7000/admin/rebalance-requests/<REQUEST_ID>/approve \
  -H "Authorization: Bearer $TOKEN"

# 8. Or cancel it as the agent, freeing the wallet for a new request
curl -s -X DELETE localhost:7000/agents/<AGENT_ID>/rebalances/<REQUEST_ID> \
  -H "Authorization: Bearer <AGENT_TOKEN>"

# 9. Or delete it as the admin (clears a stuck request)
curl -s -X DELETE localhost:7000/admin/rebalance-requests/<REQUEST_ID> \
  -H "Authorization: Bearer $TOKEN"
```

## Useful notes

- Data persists across restarts by design; delete `data/floatguard.db` (or point
  `FLOATGUARD_DB_URL` elsewhere) for a clean slate.
- Tokens never expire on their own — signing out (`POST /auth/logout`) deletes the
  session. To force everyone out during development, clear the `sessions` table.
- Every AGENT account ends up with exactly two wallets (`UNIQUE(agent_id, type)`).
  An admin-created agent (`POST /agents`) starts with none until wallets are added.
- There is no endpoint that lists a session's operation history — the UI keeps its
  transaction log in the browser. Rebalance requests **are** persisted and can be
  listed per agent or per administrator.
- Rebuilding is required after editing Java **or** frontend files (the pages are
  packaged into the jar). Stop the running server first, then
  `./mvnw clean package`.
