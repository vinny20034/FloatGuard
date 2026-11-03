# FloatGuard

FloatGuard is a web app for mobile-money agents to track their physical cash and
electronic float (e-float), record customer transactions, and request float
rebalances. It includes an agent dashboard and an administrator dashboard.

The balances are entered and updated in FloatGuard; the app does **not** connect
to a mobile-money provider or move real money.

## What it does

- Lets agents create an account with opening cash and e-float balances.
- Shows cash, e-float, combined balance, low-float warnings, and estimated runway.
- Records customer transactions as paired balance movements:
  - **Customer cash-in:** cash increases and e-float decreases.
  - **Customer cash-out:** cash decreases and e-float increases.
- Checks both balances before saving. If either side would become negative, the
  transaction is rejected and neither balance nor ledger is changed.
- Stores each side of a successful transaction in the SQLite operation ledger.
- Lets agents request a cash or e-float top-up and administrators review and
  approve requests.
- Lets administrators view agents and balances and manually assign float.

Agent dashboard transaction history is kept in that browser's local storage.
Wallet balances, accounts, rebalance requests, and the operation ledger are stored
in SQLite.

## Requirements

Anyone who clones this project must install both of these before it will build:

- **JDK 17 or newer** — verify with `java -version`
- **Maven 3.9 or newer** — verify with `mvn -v`. Download it from
  <https://maven.apache.org/download.cgi> and add its `bin` folder to `PATH`.

If you would rather not install Maven, use the bundled Maven Wrapper instead. It
downloads Maven for you on first run, so only a JDK is required.

There is no separate Node.js/frontend build step. Maven packages the HTML, CSS,
and JavaScript into the runnable Java application.

## Run the application

Make sure the requirements above are installed first. Without Maven the build
stops with `'mvn' is not recognized as the name of a cmdlet, function, script
file, or operable program`.

Then, in a terminal, go to the project folder (the folder that contains `pom.xml`),
build it, and start the server:

```powershell
cd FloatGuard
mvn clean package
java -jar target\floatguard.jar
```

- `cd FloatGuard` is only for a fresh clone: `git clone` creates a folder named
  `FloatGuard`, so you step into it once. If your terminal is **already** inside
  that folder, skip the `cd` — running it again fails with
  `Cannot find path ...\FloatGuard\FloatGuard because it does not exist`.
  Not sure where you are? `dir` lists `pom.xml` when you are in the right folder.
- `mvn clean package` compiles the Java code and packages the frontend into
  `target\floatguard.jar`.
- `java -jar target\floatguard.jar` starts the live server.

If Maven is not installed, use the bundled wrapper instead. It downloads Maven
on first use, so a JDK is all that is required:

```powershell
.\mvnw.cmd clean package
java -jar target\floatguard.jar
```

On macOS or Linux, use `./mvnw clean package` instead.

When the server reports that it is listening, open
[http://localhost:7000](http://localhost:7000). The sign-in and registration
page, agent dashboard, administrator dashboard, and API are all served by this
same process. Keep the terminal open while using the app; press `Ctrl+C` to stop
the server.

### Run from VS Code

Open the outer `floatguard` workspace folder in VS Code. Its
**Run FloatGuard frontend and backend** task builds and starts the server when
the folder opens. You can also start it from **Terminal → Run Task**. Maven and
Java must be installed and available on `PATH`.

### Configuration

Defaults are read from environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `7000` | HTTP port |
| `FLOATGUARD_DB_URL` | `jdbc:sqlite:data/floatguard.db` | SQLite JDBC URL |
| `FLOATGUARD_ADMIN_INVITE_CODE` | `FL12` | Code required for admin registration; multiple comma-separated codes are accepted |

For example, set these in PowerShell before starting the server:

```powershell
$env:PORT = "7000"
$env:FLOATGUARD_DB_URL = "jdbc:sqlite:data/floatguard.db"
$env:FLOATGUARD_ADMIN_INVITE_CODE = "replace-with-a-private-code"
java -jar target\floatguard.jar
```

The database path is relative to the directory where Java is started. Run the
commands from the `FloatGuard` project directory to use its `data` folder. The
schema and missing database directory are created automatically. Choose a private
admin invite code before making the application available to other people.

The frontend can also be opened with Live Server on `localhost:5500` or
`localhost:5501` while the Java backend is running on port `7000`. Do not open
`index.html` directly with `file://`.

## First use

1. Register an **Agent** account. Set its name, location, and opening cash and
   e-float balances. FloatGuard creates both wallets with those balances.
2. Sign in to record customer cash-ins and cash-outs. The dashboard refreshes
   both wallet balances after each successful transaction.
3. If a float needs replenishing, open **Rebalance**, select the wallet, and
   submit a request.
4. Register or sign in as an **Administrator** using the configured invite code.
   Review pending requests and approve them from the admin dashboard.

## Customer transaction API

The dashboard sends both customer transaction types to the Java API. The
`walletId` must be the agent's **CASH** wallet ID; FloatGuard finds that agent's
EFLOAT wallet and applies the matching movement.

```http
POST /deposit
Content-Type: application/json

{ "walletId": "<CASH_WALLET_ID>", "amount": 100 }
```

`POST /withdraw` accepts the same body. A successful response includes the
primary cash movement and a `pairedOperation` object describing the e-float
movement and its resulting balance. Both entries and wallet updates are committed
in one database transaction. Invalid amounts return `400`; unknown wallets return
`404`; insufficient cash or e-float returns `409` without a partial update.

## Other API routes

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Register an agent or an invited administrator |
| `POST` | `/auth/login` | Sign in |
| `POST` | `/auth/logout` | Sign out |
| `GET` | `/auth/me` | Get the signed-in account |
| `GET` | `/agents/{id}/runway` | Get agent balances, runway estimates, and warnings |
| `GET` / `POST` | `/agents/{id}/rebalances` | View or submit rebalance requests |
| `GET` | `/admin/agents` | List agent balances and warnings |
| `POST` | `/admin/agents/{id}/assign-float` | Manually top up one wallet |
| `GET` | `/admin/rebalance-requests` | List rebalance requests |
| `PUT` | `/admin/rebalance-requests/{id}/approve` | Approve and credit a request |

Protected routes require the bearer token returned by `/auth/login` or
`/auth/register`. Administrators can access admin routes; agents can access only
their own data.

## Project layout

```text
FloatGuard/
├── src/main/java/com/floatguard/    Java API, services, domain, and SQLite persistence
├── index.html                       Sign-in and registration page
├── agent.html                       Agent dashboard
├── admin.html                       Administrator dashboard
├── *.js, styles.css                 Frontend behavior and styles
├── data/                            Default SQLite database location
└── pom.xml                          Maven build configuration
```

The backend uses Java, Javalin, and SQLite (plain JDBC). The frontend is served
from the packaged JAR, so editing frontend or Java source requires rebuilding
with `mvn clean package` before restarting the app.

Stop the running server with `Ctrl+C` before rebuilding. On Windows the running
app keeps `target\floatguard.jar` locked, so `mvn clean` fails with
"The process cannot access the file ... used by another process" if the server
is still up.
