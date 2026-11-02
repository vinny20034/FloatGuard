/*
 * FLOATGUARD - ADMINISTRATOR CONSOLE
 *
 * Runs on admin.html behind the shared auth guard (auth.js). Endpoints used:
 *   GET  /admin/agents                   every agent with balances + warnings
 *   POST /admin/agents/{id}/assign-float top up one of an agent's wallets
 *
 * Only ADMIN tokens reach these routes; the backend rejects anyone else.
 */

let admin = null;
let agents = [];
let assignTarget = null;

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

function walletOf(agent, type) {
  return (agent.wallets || []).find(w => w.type === type) || { balance: 0, warning: "OK" };
}

function warningClass(warning) {
  if (warning === "CRITICAL") return "status low";
  if (warning === "WARNING") return "status pending";
  return "status available";
}

function warningText(warning) {
  if (warning === "CRITICAL") return "Critical";
  if (warning === "WARNING") return "Running low";
  return "Healthy";
}

/* ------------------------------------------------------------------ *
 * Data loading + rendering
 * ------------------------------------------------------------------ */

async function loadAgents() {
  const res = await Auth.api("GET", "/admin/agents");

  if (!res.ok) {
    $("agentRows").innerHTML =
      `<tr><td colspan="6" class="empty-cell">${escapeHTML(res.message)}</td></tr>`;
    return;
  }

  agents = res.data.agents || [];
  renderStats();
  renderAgents();
}

function renderStats() {
  const cash = agents.reduce((sum, a) => sum + Number(walletOf(a, "CASH").balance), 0);
  const efloat = agents.reduce((sum, a) => sum + Number(walletOf(a, "EFLOAT").balance), 0);
  const risk = agents.filter(a => a.overallWarning && a.overallWarning !== "OK").length;

  $("statAgents").textContent = agents.length;
  $("statCash").textContent = money(cash);
  $("statEFloat").textContent = money(efloat);
  $("statRisk").textContent = risk;
}

function agentRow(agent) {
  const cash = walletOf(agent, "CASH");
  const efloat = walletOf(agent, "EFLOAT");

  return `<tr>
    <td>${escapeHTML(agent.name)}</td>
    <td>${escapeHTML(agent.location)}</td>
    <td>${money(cash.balance)}</td>
    <td>${money(efloat.balance)}</td>
    <td><span class="${warningClass(agent.overallWarning)}">${warningText(agent.overallWarning)}</span></td>
    <td data-action>
      <button
        type="button"
        class="assign-button"
        data-assign="${escapeHTML(agent.id)}"
        data-name="${escapeHTML(agent.name)}"
      >Assign float</button>
    </td>
  </tr>`;
}

function renderAgents() {
  const body = $("agentRows");
  const query = ($("agentSearch")?.value || "").trim().toLowerCase();

  const filtered = agents.filter(a =>
    a.name.toLowerCase().includes(query) || a.location.toLowerCase().includes(query));

  if (!agents.length) {
    body.innerHTML = `<tr><td colspan="6" class="empty-cell">No agents have registered yet.</td></tr>`;
    return;
  }

  body.innerHTML = filtered.length
    ? filtered.map(agentRow).join("")
    : `<tr><td colspan="6" class="empty-cell">No agent matches that search.</td></tr>`;
}

/* ------------------------------------------------------------------ *
 * Assign float
 * ------------------------------------------------------------------ */

function openAssign(agentId, agentName) {
  assignTarget = agentId;

  $("assignAgentLabel").textContent = agentName;
  $("assignAmount").value = "";
  $("assignWalletType").value = "CASH";

  const message = $("assignMessage");
  message.style.display = "none";
  message.className = "message";

  $("assignModal").hidden = false;
  $("assignAmount").focus();
}

function closeAssign() {
  $("assignModal").hidden = true;
  assignTarget = null;
}

function showAssignMessage(message) {
  const box = $("assignMessage");
  box.textContent = message;
  box.className = "message error";
  box.style.display = "block";
}

async function submitAssign(event) {
  event.preventDefault();
  if (!assignTarget) return;

  const walletType = $("assignWalletType").value;
  const amount = Number($("assignAmount").value);

  if (!Number.isFinite(amount) || amount <= 0) {
    showAssignMessage("Enter an amount greater than zero.");
    return;
  }

  const res = await Auth.api(
    "POST",
    `/admin/agents/${encodeURIComponent(assignTarget)}/assign-float`,
    { walletType, amount }
  );

  if (!res.ok) {
    showAssignMessage(res.message);
    return;
  }

  const agentName = $("assignAgentLabel").textContent;
  closeAssign();
  await loadAgents();
  showMessage(`Assigned ${money(res.data.amount)} to ${agentName}'s ${walletType === "EFLOAT" ? "e-float" : "cash float"}.`);
}

/* ------------------------------------------------------------------ *
 * Wire up
 * ------------------------------------------------------------------ */

document.addEventListener("DOMContentLoaded", () => {
  admin = Auth.guard("ADMIN");
  if (!admin) return; // a redirect was issued

  if ($("userName")) $("userName").textContent = admin.username;
  if ($("adminName")) $("adminName").textContent = admin.username || "Administrator";

  $("agentSearch")?.addEventListener("input", renderAgents);
  $("refreshBtn")?.addEventListener("click", () => loadAgents());
  $("signOutBtn")?.addEventListener("click", () => Auth.logout());

  $("agentRows")?.addEventListener("click", event => {
    const button = event.target.closest("[data-assign]");
    if (button) openAssign(button.dataset.assign, button.dataset.name);
  });

  $("assignForm")?.addEventListener("submit", submitAssign);
  $("assignClose")?.addEventListener("click", closeAssign);

  $("assignModal")?.addEventListener("click", event => {
    if (event.target === $("assignModal")) closeAssign();
  });

  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !$("assignModal").hidden) closeAssign();
  });

  $("mobileMenuBtn")?.addEventListener("click", () => {
    $("sidebar")?.classList.toggle("open");
  });

  loadAgents();
});
