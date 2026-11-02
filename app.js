/*
 * FLOATGUARD - AGENT DASHBOARD
 *
 * Runs on agent.html behind the shared auth guard (auth.js). The bearer token
 * identifies the agent, so the dashboard loads THAT agent's wallets rather than
 * creating one. Endpoints used:
 *   GET  /agents/{id}/runway      balances + warnings (id = signed-in agent)
 *   GET  /agents/{id}/rebalances  floats vs threshold + the agent's requests
 *   POST /agents/{id}/rebalances  raise a PENDING request for one float
 *   POST /deposit                 log a deposit into ONE wallet
 *   POST /withdraw                log a withdrawal from ONE wallet
 *   PUT  /rebalance/{id}          mark a rebalance request COMPLETED
 *
 * Balances and rebalance status live in the backend (SQLite). There is no
 * operation-history endpoint yet, so the transaction list is kept in this
 * browser, keyed per agent.
 */

const HISTORY_KEY = "floatguard_history_v1";

// Signed-in account (from auth.js) and the agent it owns.
let user = null;
// { agentId, agentName, location, wallets: { CASH: {...}, EFLOAT: {...} }, overallWarning, shortId }
let session = null;
// { threshold, wallets: [...], requests: [...] } loaded from the backend.
let rebalanceData = { threshold: 0, wallets: [], requests: [] };
let history = [];

const $ = (id) => document.getElementById(id);

function money(value) {
  return new Intl.NumberFormat("en-ZM", {
    style: "currency",
    currency: "ZMW",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(value) || 0);
}

function escapeHTML(value) {
  return String(value).replace(/[&<>"']/g, c => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[c]);
}

function showMessage(message, type = "success") {
  const box = $("message");
  if (!box) return;

  box.textContent = message;
  box.className = `message ${type}`;
  box.style.display = "block";

  clearTimeout(showMessage.timer);
  showMessage.timer = setTimeout(() => {
    box.style.display = "none";
    box.className = "message";
  }, 4000);
}

function walletLabel(type) {
  return type === "EFLOAT" ? "E-float" : "Cash float";
}

function formatDateTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value || "") : date.toLocaleString();
}

/**
 * Display id for the signed-in agent: the initial of the account name followed
 * by a stable number between 1 and 100 (e.g. "M42"). Derived from the account so
 * the sidebar and the header chip always agree.
 */
function agentDisplayId(displayName, seed) {
  const name = String(displayName || "").trim();
  const initial = (name.charAt(0) || "A").toUpperCase();
  const source = String(seed || name || "agent");

  let hash = 0;
  for (let i = 0; i < source.length; i++) {
    hash = (hash * 31 + source.charCodeAt(i)) % 100000;
  }
  return initial + ((hash % 100) + 1);
}

/* ------------------------------------------------------------------ *
 * API helper (authenticated via auth.js)
 * ------------------------------------------------------------------ */

function api(method, path, body) {
  return Auth.api(method, path, body);
}

/* ------------------------------------------------------------------ *
 * Per-agent browser persistence for the session's activity log
 * ------------------------------------------------------------------ */

function historyKey() {
  return `${HISTORY_KEY}:${session?.agentId || "unknown"}`;
}

function saveHistory() {
  localStorage.setItem(historyKey(), JSON.stringify(history));
}

function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem(historyKey())) || [];
  } catch {
    return [];
  }
}

/* ------------------------------------------------------------------ *
 * Bootstrap
 * ------------------------------------------------------------------ */

async function refreshState() {
  const res = await api("GET", `/agents/${encodeURIComponent(session.agentId)}/runway`);
  if (!res.ok) return false;

  const data = res.data;
  session.agentName = data.agentName;
  session.location = data.location;
  session.overallWarning = data.overallWarning;

  const wallets = {};
  (data.wallets || []).forEach(w => { wallets[w.type] = w; });
  session.wallets = wallets;

  updateDashboard();
  return true;
}

async function bootstrap() {
  user = Auth.guard("AGENT");
  if (!user) return; // a redirect to the sign-in page was issued

  if (!user.agentId) {
    showMessage("This account is not linked to an agent. Contact an administrator.", "error");
    return;
  }

  session = { agentId: user.agentId, agentName: "", location: "", wallets: {}, overallWarning: "OK" };
  history = loadHistory();
  rebalanceLog = loadRebalances();

  if ($("userName")) $("userName").textContent = user.username;

  const ok = await refreshState();
  if (!ok) showMessage("Could not load your float data. Please sign in again.", "error");
}

/* ------------------------------------------------------------------ *
 * Dashboard rendering
 * ------------------------------------------------------------------ */

function warningStatus(id, warning, kind) {
  const el = $(id);
  if (!el) return;

  const label = kind === "EFLOAT" ? "E-Float" : "Cash";

  if (warning === "CRITICAL") {
    el.textContent = `${label} Critical`;
    el.className = "status low";
  } else if (warning === "WARNING") {
    el.textContent = `${label} Running Low`;
    el.className = "status pending";
  } else {
    el.textContent = `${label} Available`;
    el.className = "status available";
  }
}

function updateDashboard() {
  if (!session) return;

  const cash = session.wallets.CASH || { balance: 0, warning: "OK" };
  const efloat = session.wallets.EFLOAT || { balance: 0, warning: "OK" };

  if ($("agentName")) $("agentName").textContent = session.agentName || "Agent";
  if ($("welcomeName")) $("welcomeName").textContent = session.agentName || "Agent";
  if ($("agentId")) $("agentId").textContent = session.agentId || "—";

  if ($("cashBalance")) $("cashBalance").textContent = money(cash.balance);
  if ($("eFloatBalance")) $("eFloatBalance").textContent = money(efloat.balance);
  if ($("totalBalance")) {
    $("totalBalance").textContent = money(Number(cash.balance) + Number(efloat.balance));
  }

  warningStatus("cashStatus", cash.warning, "CASH");
  warningStatus("eFloatStatus", efloat.warning, "EFLOAT");

  const warning = $("floatWarning");
  if (warning) {
    const low = session.overallWarning && session.overallWarning !== "OK";
    warning.style.display = low ? "block" : "none";
    warning.textContent =
      "A float is running low. A rebalance request has been raised automatically — open the Rebalance tab to review it.";
  }

  renderHistory();
  renderRecentTransactions();
  renderRebalanceRequests();
  updateTransactionStats();
}

function transactionRow(t, compact = false) {
  const type =
    t.type === "deposit" ? "Customer deposit" : "Customer withdrawal";

  if (compact) {
    return `<tr>
      <td>${escapeHTML(t.reference)}</td>
      <td><span class="type-pill ${t.type}">${type}</span></td>
      <td>${money(t.amount)}</td>
      <td>${escapeHTML(t.date)}</td>
    </tr>`;
  }

  return `<tr>
    <td>${escapeHTML(t.reference)}</td>
    <td><span class="type-pill ${t.type}">${t.type === "deposit" ? "Deposit" : "Withdrawal"}</span></td>
    <td>${escapeHTML(walletLabel(t.walletType))}</td>
    <td>${money(t.amount)}</td>
    <td>${escapeHTML(t.date)}</td>
  </tr>`;
}

function renderHistory(list = history) {
  const body = $("transactionHistory");
  if (!body) return;

  body.innerHTML = list.length
    ? list.map(t => transactionRow(t)).join("")
    : `<tr><td colspan="5" class="empty-cell">No transactions recorded yet.</td></tr>`;
}

function renderRecentTransactions() {
  const body = $("recentTransactions");
  if (!body) return;

  const recent = history.slice(0, 5);
  body.innerHTML = recent.length
    ? recent.map(t => transactionRow(t, true)).join("")
    : `<tr><td colspan="4" class="empty-cell">No transactions recorded yet.</td></tr>`;
}

function updateTransactionStats() {
  if ($("transactionCount")) $("transactionCount").textContent = history.length;

  const deposits = history
    .filter(t => t.type === "deposit")
    .reduce((sum, t) => sum + Number(t.amount), 0);

  const withdrawals = history
    .filter(t => t.type === "withdrawal")
    .reduce((sum, t) => sum + Number(t.amount), 0);

  if ($("depositTotal")) $("depositTotal").textContent = money(deposits);
  if ($("withdrawalTotal")) $("withdrawalTotal").textContent = money(withdrawals);
}

function searchTransactions() {
  const query = ($("historySearch")?.value || "").trim().toLowerCase();

  const filtered = history.filter(t =>
    t.reference.toLowerCase().includes(query) ||
    t.type.toLowerCase().includes(query) ||
    walletLabel(t.walletType).toLowerCase().includes(query) ||
    t.date.toLowerCase().includes(query)
  );

  renderHistory(filtered);
}

/* ------------------------------------------------------------------ *
 * Transactions
 * ------------------------------------------------------------------ */

function amountFrom(id) {
  const field = $(id);
  const value = Number(field?.value);

  if (!Number.isFinite(value) || value <= 0) {
    showMessage("Enter an amount greater than zero.", "error");
    return null;
  }

  if (Math.round(value * 100) !== value * 100) {
    showMessage("Enter an amount with no more than two decimal places.", "error");
    return null;
  }

  return value;
}

async function recordTransaction(kind, amount, walletType) {
  const wallet = session?.wallets?.[walletType];
  if (!wallet) {
    showMessage("That wallet is not available.", "error");
    return false;
  }

  const path = kind === "deposit" ? "/deposit" : "/withdraw";
  const res = await api("POST", path, { walletId: wallet.id, amount });

  if (!res.ok) {
    showMessage(res.message, "error");
    return false;
  }

  const data = res.data;

  history.unshift({
    reference: "FG-" + String(data.id).replace(/-/g, "").slice(0, 8).toUpperCase(),
    type: kind,
    walletType,
    amount: data.amount,
    balanceAfter: data.balanceAfter,
    date: new Date(data.timestamp).toLocaleString()
  });
  saveHistory();

  if (data.rebalanceRequestId) {
    addRebalance(data.rebalanceRequestId, walletType);
  }

  await refreshState();

  showMessage(
    `${kind === "deposit" ? "Deposit" : "Withdrawal"} of ${money(amount)} recorded on the ${walletLabel(walletType)}.`
  );

  return true;
}

async function handleDeposit(event) {
  event.preventDefault();

  const amount = amountFrom("depositAmount");
  if (amount === null) return;

  const walletType = $("depositFloatType").value;
  if (await recordTransaction("deposit", amount, walletType)) {
    $("depositForm").reset();
  }
}

async function handleWithdrawal(event) {
  event.preventDefault();

  const amount = amountFrom("withdrawAmount");
  if (amount === null) return;

  const walletType = $("withdrawFloatType").value;
  if (await recordTransaction("withdrawal", amount, walletType)) {
    $("withdrawalForm").reset();
  }
}

/* ------------------------------------------------------------------ *
 * Rebalance log
 * ------------------------------------------------------------------ */

function addRebalance(id, walletType) {
  if (rebalanceLog.some(r => r.id === id)) return;

  rebalanceLog.unshift({
    id,
    walletType,
    status: "PENDING",
    date: new Date().toLocaleString()
  });

  saveRebalances();
}

function renderRebalanceRequests() {
  const list = $("rebalanceRequests");
  if (!list) return;

  if (!rebalanceLog.length) {
    list.innerHTML = `<p class="empty-cell">No rebalance requests yet.</p>`;
    return;
  }

  list.innerHTML = rebalanceLog.map(request => `
    <div class="rebalance-item">
      <div>
        <strong>${request.walletType === "EFLOAT" ? "E-float" : "Cash"} rebalance</strong>
        <p>Request ${escapeHTML(String(request.id).slice(0, 8))} · ${escapeHTML(request.date)}</p>
      </div>

      <span class="status ${request.status === "PENDING" ? "pending" : "completed"}">
        ${escapeHTML(request.status)}
      </span>

      ${request.status === "PENDING"
        ? `<button type="button" data-complete="${escapeHTML(String(request.id))}">Mark completed</button>`
        : ""}
    </div>
  `).join("");
}

async function completeRebalance(id) {
  const request = rebalanceLog.find(r => r.id === id);
  if (!request) return;

  const res = await api("PUT", `/rebalance/${encodeURIComponent(id)}`);

  if (!res.ok) {
    showMessage(res.message, "error");
    return;
  }

  request.status = res.data.status || "COMPLETED";
  saveRebalances();
  renderRebalanceRequests();

  showMessage("Rebalance request marked as completed in the database.");
}

/* ------------------------------------------------------------------ *
 * Export + navigation
 * ------------------------------------------------------------------ */

function exportTransactions() {
  if (!history.length) {
    showMessage("There are no transactions to export yet.", "error");
    return;
  }

  const rows = [
    ["Reference", "Type", "Float", "Amount ZMW", "Balance after ZMW", "Date"],
    ...history.map(t => [
      t.reference,
      t.type,
      walletLabel(t.walletType),
      t.amount,
      t.balanceAfter,
      t.date
    ])
  ];

  const csv = rows
    .map(row => row.map(v => `"${String(v).replace(/"/g, '""')}"`).join(","))
    .join("\r\n");

  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);

  const a = document.createElement("a");
  a.href = url;
  a.download = "FloatGuard_Transaction_History.csv";
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);

  showMessage("Transaction history exported.");
}

function navigate(sectionId) {
  document.querySelectorAll(".dashboard-section")
    .forEach(section => { section.hidden = section.id !== sectionId; });

  document.querySelectorAll(".nav-link")
    .forEach(link => {
      link.classList.toggle("active", link.dataset.section === sectionId);
    });

  const titles = {
    overview: "Overview",
    transactions: "Transactions",
    rebalance: "Rebalance"
  };

  if ($("pageTitle")) {
    $("pageTitle").textContent = titles[sectionId] || "Overview";
  }

  document.querySelectorAll(".sidebar").forEach(sidebar => sidebar.classList.remove("open"));

  window.scrollTo({ top: 0, behavior: "smooth" });
}

/* ------------------------------------------------------------------ *
 * Wire up
 * ------------------------------------------------------------------ */

document.addEventListener("DOMContentLoaded", () => {

  if ($("todayDate")) {
    $("todayDate").textContent =
      new Date().toLocaleDateString("en-ZM", {
        weekday: "short",
        day: "numeric",
        month: "short",
        year: "numeric"
      });
  }

  $("depositForm")?.addEventListener("submit", handleDeposit);
  $("withdrawalForm")?.addEventListener("submit", handleWithdrawal);

  $("historySearch")?.addEventListener("input", searchTransactions);
  $("exportBtn")?.addEventListener("click", exportTransactions);

  $("signOutBtn")?.addEventListener("click", () => Auth.logout());

  document.querySelectorAll(".nav-link").forEach(link => {
    link.addEventListener("click", event => {
      event.preventDefault();
      navigate(link.dataset.section);
    });
  });

  document.querySelectorAll("[data-go]").forEach(button => {
    button.addEventListener("click", () => navigate(button.dataset.go));
  });

  $("mobileMenuBtn")?.addEventListener("click", () => {
    $("sidebar")?.classList.toggle("open");
  });

  $("rebalanceRequests")?.addEventListener("click", event => {
    const button = event.target.closest("[data-complete]");
    if (button) completeRebalance(button.dataset.complete);
  });

  bootstrap();
});
