/*
 * FLOATGUARD - DEMO FRONTEND
 * No backend connection is required while DEMO_MODE is true.
 * Demo data is saved in browser localStorage.
 */

const DEMO_MODE = false;
const API_BASE_URL = "http://localhost:7000";

const DEFAULT_AGENT = {
  id: 1,
  name: "Demo Agent",
  cashBalance: 5000,
  eFloatBalance: 8000
};

let agent = { ...DEFAULT_AGENT };
let transactions = [];
let rebalanceRequests = [];

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
  }, 3500);
}

function saveDemoData() {
  if (!DEMO_MODE) return;

  localStorage.setItem("floatguard_agent", JSON.stringify(agent));
  localStorage.setItem("floatguard_transactions", JSON.stringify(transactions));
  localStorage.setItem("floatguard_rebalances", JSON.stringify(rebalanceRequests));
}

function loadDemoData() {
  if (!DEMO_MODE) return;

  try {
    agent = JSON.parse(localStorage.getItem("floatguard_agent")) || {
      ...DEFAULT_AGENT
    };

    transactions = JSON.parse(localStorage.getItem("floatguard_transactions")) || [];

    rebalanceRequests = JSON.parse(localStorage.getItem("floatguard_rebalances")) || [];

  } catch (error) {
    resetDemoData(false);
  }

  updateDashboard();
}

function resetDemoData(showNotice = true) {
  agent = { ...DEFAULT_AGENT };
  transactions = [];
  rebalanceRequests = [];

  saveDemoData();
  updateDashboard();

  if (showNotice) {
    showMessage("Demo data has been reset.");
  }
}

function dateLabel() {
  return new Date().toLocaleString();
}

function reference() {
  return "FG-" + Date.now().toString().slice(-8);
}

function amountFrom(id) {
  const field = $(id);
  const value = Number(field?.value);

  if (
    !Number.isFinite(value) ||
    value <= 0 ||
    Math.round(value * 100) !== value * 100
  ) {
    showMessage(
      "Enter a valid amount with no more than two decimal places.",
      "error"
    );

    return null;
  }

  return value;
}

function updateDashboard() {
  if ($("agentName")) $("agentName").textContent = agent.name;
  if ($("welcomeName")) $("welcomeName").textContent = agent.name;
  if ($("agentId")) $("agentId").textContent = agent.id;

  if ($("cashBalance")) {
    $("cashBalance").textContent = money(agent.cashBalance);
  }

  if ($("eFloatBalance")) {
    $("eFloatBalance").textContent = money(agent.eFloatBalance);
  }

  if ($("totalBalance")) {
    $("totalBalance").textContent = money(
      agent.cashBalance + agent.eFloatBalance
    );
  }

  updateStatus(
    "cashStatus",
    agent.cashBalance,
    "Cash Available",
    "Low Cash"
  );

  updateStatus(
    "eFloatStatus",
    agent.eFloatBalance,
    "E-Float Available",
    "Low E-Float"
  );

  const warning = $("floatWarning");

  if (warning) {
    const low =
      agent.cashBalance < 1000 ||
      agent.eFloatBalance < 1000;

    warning.style.display = low ? "block" : "none";

    warning.textContent =
      "One of your float balances is running low. Consider creating a rebalance request.";
  }

  renderHistory();
  renderRecentTransactions();
  renderRebalanceRequests();
  updateTransactionStats();
}

function updateStatus(id, value, normal, low) {
  const el = $(id);

  if (!el) return;

  const isLow = value < 1000;

  el.textContent = isLow ? low : normal;

  el.className = `status ${isLow ? "low" : "available"}`;
}

function recordTransaction(type, amount) {

  if (type === "deposit") {

    // Customer hands cash to the agent; agent sends e-float.

    if (agent.eFloatBalance < amount) {
      showMessage(
        "Insufficient e-float to complete this customer deposit.",
        "error"
      );

      return false;
    }

    agent.cashBalance += amount;
    agent.eFloatBalance -= amount;

  } else if (type === "withdrawal") {

    // Agent hands cash to the customer; agent receives e-float.

    if (agent.cashBalance < amount) {
      showMessage(
        "Insufficient cash to complete this customer withdrawal.",
        "error"
      );

      return false;
    }

    agent.cashBalance -= amount;
    agent.eFloatBalance += amount;

  } else {

    showMessage("Unknown transaction type.", "error");

    return false;
  }

  transactions.unshift({
    reference: reference(),
    type,
    amount,
    cashAfter: agent.cashBalance,
    eFloatAfter: agent.eFloatBalance,
    date: dateLabel(),
    timestamp: Date.now()
  });

  saveDemoData();
  updateDashboard();

  showMessage(
    `${type === "deposit" ? "Customer deposit" : "Customer withdrawal"} recorded successfully.`
  );

  return true;
}

function handleDeposit(event) {
  event.preventDefault();

  const amount = amountFrom("depositAmount");

  if (amount === null) return;

  if (recordTransaction("deposit", amount)) {
    $("depositForm").reset();
  }
}

function handleWithdrawal(event) {
  event.preventDefault();

  const amount = amountFrom("withdrawAmount");

  if (amount === null) return;

  if (recordTransaction("withdrawal", amount)) {
    $("withdrawalForm").reset();
  }
}

function transactionRow(t, compact = false) {
  const type =
    t.type === "deposit"
      ? "Customer deposit"
      : "Customer withdrawal";

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
    <td><span class="type-pill ${t.type}">${type}</span></td>
    <td>${money(t.amount)}</td>
    <td>${money(t.cashAfter)}</td>
    <td>${money(t.eFloatAfter)}</td>
    <td>${escapeHTML(t.date)}</td>
  </tr>`;
}

function renderHistory(list = transactions) {
  const body = $("transactionHistory");

  if (!body) return;

  body.innerHTML = list.length
    ? list.map(t => transactionRow(t)).join("")
    : `<tr><td colspan="6" class="empty-cell">No transactions recorded yet.</td></tr>`;
}

function renderRecentTransactions() {
  const body = $("recentTransactions");

  if (!body) return;

  const recent = transactions.slice(0, 5);

  body.innerHTML = recent.length
    ? recent.map(t => transactionRow(t, true)).join("")
    : `<tr><td colspan="4" class="empty-cell">No transactions recorded yet.</td></tr>`;
}

function updateTransactionStats() {
  if ($("transactionCount")) {
    $("transactionCount").textContent = transactions.length;
  }

  const deposits = transactions
    .filter(t => t.type === "deposit")
    .reduce((sum, t) => sum + t.amount, 0);

  const withdrawals = transactions
    .filter(t => t.type === "withdrawal")
    .reduce((sum, t) => sum + t.amount, 0);

  if ($("depositTotal")) {
    $("depositTotal").textContent = money(deposits);
  }

  if ($("withdrawalTotal")) {
    $("withdrawalTotal").textContent = money(withdrawals);
  }
}

function searchTransactions() {
  const query = ($("historySearch")?.value || "")
    .trim()
    .toLowerCase();

  const filtered = transactions.filter(t =>
    t.reference.toLowerCase().includes(query) ||
    t.type.toLowerCase().includes(query) ||
    t.date.toLowerCase().includes(query)
  );

  renderHistory(filtered);
}

function exportTransactions() {
  if (!transactions.length) {
    showMessage(
      "There are no transactions to export yet.",
      "error"
    );

    return;
  }

  const rows = [
    [
      "Reference",
      "Type",
      "Amount ZMW",
      "Cash after ZMW",
      "E-float after ZMW",
      "Date"
    ],

    ...transactions.map(t => [
      t.reference,
      t.type,
      t.amount,
      t.cashAfter,
      t.eFloatAfter,
      t.date
    ])
  ];

  const csv = rows
    .map(row =>
      row.map(v =>
        `"${String(v).replace(/"/g, '""')}"`
      ).join(",")
    )
    .join("\r\n");

  const blob = new Blob([csv], {
    type: "text/csv;charset=utf-8;"
  });

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

function createRebalanceRequest(event) {
  event.preventDefault();

  const amount = amountFrom("rebalanceAmount");

  if (amount === null) return;

  const floatType = $("floatType").value;

  rebalanceRequests.unshift({
    id: Date.now(),
    floatType,
    amount,
    status: "PENDING",
    date: dateLabel()
  });

  saveDemoData();
  updateDashboard();

  $("rebalanceForm").reset();

  showMessage(
    "Rebalance request saved as pending in demo mode."
  );
}

function renderRebalanceRequests() {
  const list = $("rebalanceRequests");

  if (!list) return;

  if (!rebalanceRequests.length) {
    list.innerHTML =
      `<p class="empty-cell">No rebalance requests yet.</p>`;

    return;
  }

  list.innerHTML = rebalanceRequests.map(request => `
    <div class="rebalance-item">
      <div>
        <strong>${request.floatType === "E_FLOAT" ? "E-float requested" : "Cash requested"}</strong>
        <p>Amount: ${money(request.amount)}</p>
        <small>${escapeHTML(request.date)}</small>
      </div>

      <span class="status ${request.status === "PENDING" ? "pending" : "completed"}">
        ${escapeHTML(request.status)}
      </span>

      ${request.status === "PENDING" ? `<button type="button" data-complete="${request.id}">Mark completed</button>` : ""}
    </div>
  `).join("");
}

function completeRebalance(id) {
  const request = rebalanceRequests.find(
    r => r.id === Number(id)
  );

  if (!request) return;

  request.status = "COMPLETED";

  saveDemoData();
  updateDashboard();

  showMessage(
    "Demo request marked as completed. No float was transferred."
  );
}

function navigate(sectionId) {
  document.querySelectorAll(".dashboard-section")
    .forEach(section => {
      section.hidden = section.id !== sectionId;
    });

  document.querySelectorAll(".nav-link")
    .forEach(link => {
      link.classList.toggle(
        "active",
        link.dataset.section === sectionId
      );
    });

  const titles = {
    overview: "Overview",
    transactions: "Transactions",
    rebalance: "Rebalance"
  };

  if ($("pageTitle")) {
    $("pageTitle").textContent =
      titles[sectionId] || "Overview";
  }

  document.querySelectorAll(".sidebar")
    .forEach(sidebar => sidebar.classList.remove("open"));

  window.scrollTo({
    top: 0,
    behavior: "smooth"
  });
}

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

  loadDemoData();

  $("depositForm")?.addEventListener(
    "submit",
    handleDeposit
  );

  $("withdrawalForm")?.addEventListener(
    "submit",
    handleWithdrawal
  );

  $("rebalanceForm")?.addEventListener(
    "submit",
    createRebalanceRequest
  );

  $("historySearch")?.addEventListener(
    "input",
    searchTransactions
  );

  $("exportBtn")?.addEventListener(
    "click",
    exportTransactions
  );

  $("resetDemoBtn")?.addEventListener("click", () => {
    if (
      confirm(
        "Reset all demo balances, transactions and rebalance requests?"
      )
    ) {
      resetDemoData();
    }
  });

  document.querySelectorAll(".nav-link")
    .forEach(link => {
      link.addEventListener("click", event => {
        event.preventDefault();

        navigate(link.dataset.section);
      });
    });

  document.querySelectorAll("[data-go]")
    .forEach(button => {
      button.addEventListener("click", () => {
        navigate(button.dataset.go);
      });
    });

  $("mobileMenuBtn")?.addEventListener("click", () => {
    $("sidebar")?.classList.toggle("open");
  });

  $("rebalanceRequests")?.addEventListener("click", event => {
    const button = event.target.closest("[data-complete]");

    if (button) {
      completeRebalance(button.dataset.complete);
    }
  });

});

/*
 * BACKEND INTEGRATION NOTES:
 * - Keep DEMO_MODE = true for the presentation.
 * - When Member 4 confirms the final endpoint paths and JSON response fields,
 *   connect the forms and data loaders to those exact API contracts.
 * - Do not assume the demo's localStorage is a secure or shared database.
 * - Rebalance requests in this demo are local only: no agent broadcast,
 *   matching, acceptance workflow, or actual cash/e-float exchange.
 */