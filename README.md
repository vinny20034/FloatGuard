# FloatGuard

A small REST API that helps mobile-money agents in Zambia keep track of two floats:

- **CashFloat** — physical cash in the till
- **EFloat** — electronic float held on the mobile-money provider account

Balances are entered **manually** (there is no live connection to a provider). The
service stores everything in SQLite so state survives restarts, and serves a single
HTML + JavaScript page for quick manual testing.

---

## 1. Tech stack

| Concern | Choice |
|---|---|
| Language | Java 17 (built with any JDK ≥ 17) |
| HTTP server | Javalin 6.3.0 (Jetty 11) |
| Persistence | SQLite via `org.xerial:sqlite-jdbc` (plain JDBC, no ORM) |
| JSON | Jackson (`jackson-databind`) |
| Logging | slf4j-simple |
| Build | Maven (fat jar via `maven-shade-plugin`) |

---

## 2. Quick start

```bash
# 1. Build a runnable jar  ->  target/floatguard.jar
mvn clean package

# 2. Run it (defaults: port 7000, DB at ./data/floatguard.db)
java -jar target/floatguard.jar
```

Then open <http://localhost:7000/> in a browser, or call the API directly.

> If `mvn` is not installed, any recent Maven will do — point it at this `pom.xml`.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `7000` | HTTP port to listen on |
| `FLOATGUARD_DB_URL` | `jdbc:sqlite:data/floatguard.db` | JDBC URL of the SQLite database |

```bash
PORT=8080 FLOATGUARD_DB_URL="jdbc:sqlite:/tmp/floatguard.db" java -jar target/floatguard.jar
```

The SQLite schema is created automatically on startup if it does not exist, and the
parent directory of the database file is created if missing.

---

## 3. Project structure

```
src/main/java/com/floatguard/
├── Main.java                 entry point: config -> schema -> service -> routes -> listen
├── config/AppConfig.java     reads PORT / FLOATGUARD_DB_URL, creates the DB directory
├── api/                      REST layer — validate, delegate, map to JSON (NO business rules)
│   ├── ApiRoutes.java        registers error mapping + all route groups + JSON 404
│   ├── ErrorMapper.java      exception -> HTTP status translation
│   ├── Responses.java        JSON response / error-envelope helpers
│   ├── Validation.java       input validation helpers
│   ├── dto/                  request/response records (the wire shapes)
│   └── routes/               one handler per resource
├── error/                    typed exceptions (Validation / NotFound / Conflict)
├── service/                  orchestration: the only place domain + persistence meet
├── model/integration/        *** the domain contract + reference implementations ***
├── persistence/              Database (connection + transactions), SchemaInitializer
│   └── dao/                  Agent / Wallet / Operation / Rebalance DAOs
└── util/Json.java            Jackson read/write helpers

src/main/resources/public/index.html    single static page served at "/"
```

**Layering rule:** `api` never talks to a DAO; it only calls `FloatGuardService`.
`service` is the only place that touches both the domain objects and the database.
Business rules (non-negative balance, rebalance threshold, runway/warning) live in
`model/integration`, never in `api`.

---

## 4. REST API

All request and response bodies are JSON. Base URL is `http://localhost:7000`.

| # | Method | Path | Purpose | Success |
|---|---|---|---|---|
| 1 | `POST` | `/agents` | Register a new agent | 201 |
| 2 | `POST` | `/agents/{id}/wallets` | Create a wallet (CASH or EFLOAT) | 201 |
| 3 | `POST` | `/deposit` | Log a customer deposit | 200 |
| 4 | `POST` | `/withdraw` | Log a customer withdrawal | 200 |
| 5 | `GET` | `/agents/{id}/runway` | Balances + runway estimates + warning | 200 |
| 6 | `PUT` | `/rebalance/{id}` | Mark a rebalance request COMPLETED | 200 |

### Status codes

| Status | When | Body `error` code |
|---|---|---|
| `400` | Missing/blank field, wrong type, non-positive amount, malformed JSON | `VALIDATION_ERROR` (with per-field `details`) / `INVALID_ARGUMENT` |
| `404` | Unknown agent / wallet / rebalance id, or an unmatched route | `NOT_FOUND` |
| `409` | Duplicate wallet type for an agent | `CONFLICT` |
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

### 1) `POST /agents`

```json
// request
{ "name": "Chanda Mobile Money", "location": "Lusaka, Kamwala" }

// 201
{ "id": "5ca6fa22-...", "name": "Chanda Mobile Money", "location": "Lusaka, Kamwala",
  "createdAt": "2026-11-01T16:18:10.753540Z" }
```

Missing `name` or `location` → `400` with `details.location = "location is required and must not be blank."`.

### 2) `POST /agents/{id}/wallets`

```json
// request  (openingBalance is optional, defaults to 0)
{ "type": "CASH", "openingBalance": 1000 }

// 201
{ "id": "dfdc27d6-...", "agentId": "5ca6fa22-...", "type": "CASH",
  "balance": 1000.0, "createdAt": "2026-11-01T16:18:10.920761Z" }
```

`type` accepts `CASH`/`EFLOAT` (plus common aliases). Errors: `400` bad type or negative
opening balance, `404` unknown agent, `409` the agent already has a wallet of that type.

### 3) `POST /deposit` and 4) `POST /withdraw`

```json
// request
{ "walletId": "dfdc27d6-...", "amount": 500 }

// 200
{ "id": "1ad23b7c-...", "walletId": "dfdc27d6-...", "type": "DEPOSIT", "amount": 500.0,
  "balanceAfter": 1500.0, "balanceBefore": 1000.0,
  "timestamp": "2026-11-01T16:18:11.072759Z", "rebalanceRequestId": null }
```

- `type` is `DEPOSIT` or `WITHDRAWAL`.
- `rebalanceRequestId` is filled in when the operation leaves the float **below the
  rebalance threshold** (see §6) — it is the id to pass to `PUT /rebalance/{id}`.
- Errors: `400` missing/zero/negative amount, `404` unknown wallet,
  `409 INSUFFICIENT_FLOAT` when a withdrawal exceeds the balance.

### 5) `GET /agents/{id}/runway`

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

### 6) `PUT /rebalance/{id}`

```json
// 200
{ "id": "056fab8b-...", "walletId": "dfdc27d6-...", "status": "COMPLETED",
  "createdAt": "2026-11-01T16:18:11.098876Z", "completedAt": "2026-11-01T16:18:11.292643Z" }
```

Idempotent — completing an already-completed request returns 200 with the **original**
`completedAt`. Unknown id → `404`.

---

## 5. Connecting to the domain team's code  ← read this if you are integrating

### Ownership boundary

The domain classes are owned by **other team members**. This repo currently ships
**reference implementations** of them in `com.floatguard.model.integration` so the
application builds and runs end to end. Replace them with the team's versions when
they are ready — as long as the signatures below hold, **nothing else in the codebase
needs to change**.

| Type | Owner | Used by |
|---|---|---|
| `Agent`, `FloatWallet`, `CashFloat`, `EFloat` | domain team | `FloatGuardService`, `WalletDao` |
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
| `FloatWallet` (abstract) | `static FloatWallet create(WalletType, String id, String agentId, double balance)`; `getId/getAgentId/getType/getBalance`; `void applyOperation(Operation)`; `boolean isBelowRebalanceThreshold()`; `RebalanceRequest createRebalanceRequest()` |
| `CashFloat` / `EFloat` | extend `FloatWallet`; `getType()` returns `CASH` / `EFLOAT` |
| `Operation` (interface) | `double getAmount()`; `boolean isDeposit()`; `Instant getTimestamp()`; `default double signedAmount()`; `default String getOperationType()` |
| `CustomerDeposit` / `CustomerWithdrawal` | `new (double amount, Instant timestamp)`; must reject `amount <= 0` |
| `RebalanceRequest` | `new (id, walletId, RebalanceStatus, Instant createdAt)`; `getId/getWalletId/getStatus/getCreatedAt/getCompletedAt`; `void complete()` (idempotent) |
| `RebalanceStatus` (enum) | `PENDING`, `COMPLETED` |
| `InsufficientFloatException` | unchecked; thrown by `applyOperation` |
| `RunwayEstimator` | `static RunwayEstimate estimate(FloatWallet, List<Operation>)`; `static String overallWarning(List<RunwayEstimate>)` |
| `RunwayEstimate` (record) | `walletId, walletType, balance, runwayDays, warning` |

### Rules the domain layer owns (do NOT move these into the REST layer)

1. **Non-negative balance** — enforced only in `FloatWallet.applyOperation(...)`, which
   throws `InsufficientFloatException` (mapped to HTTP 409).
2. **Rebalance threshold** — `FloatWallet.REBALANCE_THRESHOLD` / `isBelowRebalanceThreshold()`
   decide when a rebalance request is raised. The current value `200.0` is a placeholder.
3. **Runway + warning** — `RunwayEstimator` (thresholds `CRITICAL_DAYS = 1.0`,
   `WARNING_DAYS = 3.0` are placeholders).
4. **Rebalance state transition** — `RebalanceRequest.complete()`.

### Steps to swap in the team's classes

1. Put their sources in `src/main/java/com/floatguard/model/integration` (or add the
   module as a Maven dependency in `pom.xml`, then update the imports in
   `FloatGuardService`, the DAOs, `ErrorMapper` and the route classes).
2. Keep every signature in the table above. If a signature must change, the touch
   points are limited to: `service/FloatGuardService.java`, `persistence/dao/*.java`,
   `api/routes/*.java`, `api/ErrorMapper.java`.
3. Keep the enum **names** (`CASH`, `EFLOAT`, `DEPOSIT`, `WITHDRAWAL`, `PENDING`,
   `COMPLETED`) stable — they are written to the database and read back verbatim.
4. Rebuild and re-run: `mvn clean package && java -jar target/floatguard.jar`.

### What the REST layer guarantees (and never does)

- It only **validates input**, **calls `FloatGuardService`**, and **maps results and
  exceptions to JSON**. There are no business rules in `api/`.
- It never mutates a wallet balance directly; it always goes through the domain.

---

## 6. Persistence

SQLite, plain JDBC. The schema is created on startup
([SchemaInitializer.java](src/main/java/com/floatguard/persistence/SchemaInitializer.java)):

| Table | Notes |
|---|---|
| `agents` | `id` (UUID, PK), `name`, `location`, `created_at` |
| `wallets` | PK `id`, FK `agent_id`, `type` (`CASH`/`EFLOAT`), `balance`, `UNIQUE(agent_id, type)` |
| `operations` | PK `id`, FK `wallet_id`, `type` (`DEPOSIT`/`WITHDRAWAL`), `amount > 0`, `balance_after`, `occurred_at` |
| `rebalance_requests` | PK `id`, FK `wallet_id`, `status` (`PENDING`/`COMPLETED`), `created_at`, `completed_at` |

- Connections apply `PRAGMA foreign_keys = ON` and `busy_timeout = 5000`; `journal_mode = WAL`.
- Multi-row writes run in a single transaction (`Database.inTransaction`). Business
  exceptions (NotFound / Conflict / InsufficientFloat) are **re-thrown unchanged** so
  they keep their HTTP status; only `SQLException`s are wrapped.
- The `UNIQUE(agent_id, type)` and `CHECK` constraints are safety nets; the primary
  enforcement lives in the domain/service layer.

---

## 7. Manual smoke test

With the server running:

```bash
curl -X POST localhost:7000/agents -H 'Content-Type: application/json' \
  -d '{"name":"Chanda Mobile Money","location":"Lusaka, Kamwala"}'

# copy the returned id, then:
curl -X POST localhost:7000/agents/<AGENT_ID>/wallets -H 'Content-Type: application/json' \
  -d '{"type":"CASH","openingBalance":1000}'

curl -X POST localhost:7000/deposit -H 'Content-Type: application/json' \
  -d '{"walletId":"<WALLET_ID>","amount":500}'

curl localhost:7000/agents/<AGENT_ID>/runway
```

To trigger a rebalance: withdraw enough that the balance drops **below 200**. The
withdraw response then contains a `rebalanceRequestId` — pass it to
`PUT /rebalance/<id>`.

---

## 8. Useful notes

- Data persists across restarts by design; delete `data/floatguard.db` (or point
  `FLOATGUARD_DB_URL` elsewhere) for a clean slate.
- The UI at `/` is a thin test harness: it calls the same endpoints you would from
  curl and shows the raw JSON responses.
- There is no endpoint that lists rebalance requests — the id is surfaced on the
  deposit/withdraw response (and auto-filled in the UI).
