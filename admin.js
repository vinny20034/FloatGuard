/*
 * FLOATGUARD - LOCATION-BASED SUPER-AGENT DASHBOARD
 *
 * Runs on admin.html behind the shared auth guard (auth.js). Endpoints used:
 *   GET  /admin/profile                  this super agent's profile + wallets
 *   GET  /admin/agents                   agents in this super agent's location
 *   POST /admin/agents/{id}/assign-float transfer float from the matching wallet
 *
 * Only ADMIN tokens reach these routes; the backend rejects anyone else.
 */

let admin = null;
let agents = [];
let rebalanceRequests = [];
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

async function loadSuperAgentProfile() {
  const res = await Auth.api("GET", "/admin/profile");
  if (!res.ok) {
    showMessage(res.message, "error");
    return;
  }

  const profile = res.data;
  if ($("adminName")) $("adminName").textContent = profile.name;
  if ($("adminLocation")) $("adminLocation").textContent = profile.location;
  const cash = (profile.wallets || []).find(wallet => wallet.type === "CASH");
  const efloat = (profile.wallets || []).find(wallet => wallet.type === "EFLOAT");
  if ($("superCashBalance")) $("superCashBalance").textContent = money(cash?.balance);
  if ($("superEFloatBalance")) $("superEFloatBalance").textContent = money(efloat?.balance);
  if ($("superTotalBalance")) {
    $("superTotalBalance").textContent = money((Number(cash?.balance) || 0) + (Number(efloat?.balance) || 0));
  }
}

async function loadRebalanceRequests() {
  const res = await Auth.api("GET", "/admin/rebalance-requests");
  const body = $("rebalanceRequestRows");
  if (!res.ok) {
    body.innerHTML =
      `<tr><td colspan="6" class="empty-cell">${escapeHTML(res.message)}</td></tr>`;
    return;
  }

  rebalanceRequests = res.data.requests || [];
  const pendingCount = rebalanceRequests.filter(request => request.status === "PENDING").length;
  $("pendingRequestCount").textContent = `${pendingCount} pending`;
  renderRebalanceRequests();
}

function renderRebalanceRequests() {
  const body = $("rebalanceRequestRows");
  if (!body) return;

  if (!rebalanceRequests.length) {
    body.innerHTML = `<tr><td colspan="6" class="empty-cell">No rebalance requests yet.</td></tr>`;
    return;
  }

  body.innerHTML = rebalanceRequests.map(request => {
    const pending = request.status === "PENDING";
    const requestedAt = new Date(request.createdAt).toLocaleString();
    return `<tr>
      <td>
        <strong>${escapeHTML(request.agentName)}</strong>
        <small class="muted">Agent ID: ${escapeHTML(request.agentId)}</small>
        <small class="muted">Phone: ${escapeHTML(request.agentPhoneNumber || "No phone on file")}</small>
      </td>
      <td>
        <small class="muted">Location: ${escapeHTML(request.location || "—")}</small>
        <small class="muted">Town / area: ${escapeHTML(request.area || "—")}</small>
      </td>
      <td>${request.walletType === "EFLOAT" ? "E-float" : "Cash float"}</td>
      <td>${money(request.amount)}</td>
      <td>${escapeHTML(requestedAt)}</td>
      <td data-action>
        ${pending
          ? `<span class="status pending">${request.handoffCount ? "Waiting for another super agent" : "Pending"}</span>
             <button type="button" class="assign-button" data-approve="${escapeHTML(request.id)}">Approve</button>
             <button type="button" class="outline-button compact" data-pass="${escapeHTML(request.id)}">Pass</button>`
          : `<span class="status available">Approved</span>`}
      </td>
    </tr>`;
  }).join("");
}

async function refreshDashboard() {
  await Promise.all([loadSuperAgentProfile(), loadAgents(), loadRebalanceRequests()]);
}

async function approveRebalance(id) {
  const request = rebalanceRequests.find(item => item.id === id);
  if (!request) return;

  const isEFloat = request.walletType === "EFLOAT";
  const grantedLabel = isEFloat ? "e-float" : "cash float";
  const sourceLabel = isEFloat ? "e-float" : "cash";
  const paidLabel = isEFloat ? "cash" : "e-float";
  const confirmed = window.confirm(
    `Approve ${money(request.amount)} ${grantedLabel} ` +
    `for ${request.agentName}?\n\n` +
    `Phone: ${request.agentPhoneNumber || "No phone on file"}\n` +
    `Location: ${request.location || "—"}\n` +
    `Town / area: ${request.area || "—"}\n\n` +
    `Your ${sourceLabel} wallet is debited and you receive ` +
    `${money(request.amount)} in ${paidLabel} from the agent.`
  );
  if (!confirmed) return;

  const res = await Auth.api(
    "PUT",
    `/admin/rebalance-requests/${encodeURIComponent(id)}/approve`
  );
  if (!res.ok) {
    showMessage(res.message, "error");
    await loadRebalanceRequests();
    return;
  }

  await refreshDashboard();
  showMessage(
    `Approved ${money(res.data.amount)} ${grantedLabel} for ${request.agentName}. ` +
    `You received ${money(res.data.amount)} in ${paidLabel}.`
  );
}

async function passRebalance(id) {
  const request = rebalanceRequests.find(item => item.id === id);
  if (!request) return;
  const confirmed = window.confirm(
    `Pass this request from ${request.agentName} to another super agent in ${request.location}? ` +
    "It will remain pending and the agent will be told it is waiting for another local super agent."
  );
  if (!confirmed) return;

  const res = await Auth.api(
    "PUT",
    `/admin/rebalance-requests/${encodeURIComponent(id)}/pass`
  );
  if (!res.ok) {
    showMessage(res.message, "error");
    await loadRebalanceRequests();
    return;
  }
  await refreshDashboard();
  showMessage("Request passed to another super agent in your location.");
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
    <td>
      <strong>${escapeHTML(agent.name)}</strong>
      <small class="muted">Agent ID: ${escapeHTML(agent.id)}</small>
      <small class="muted">Phone: ${escapeHTML(agent.phoneNumber || "No phone on file")}</small>
    </td>
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
  await loadSuperAgentProfile();
  showMessage(`Transferred ${money(res.data.amount)} to ${agentName}'s ${walletType === "EFLOAT" ? "e-float" : "cash float"}.`);
}

/* ------------------------------------------------------------------ *
 * Wire up
 * ------------------------------------------------------------------ */

document.addEventListener("DOMContentLoaded", () => {
  admin = Auth.guard("ADMIN");
  if (!admin) return; // a redirect was issued

  if ($("userName")) $("userName").textContent = admin.username;
  if ($("adminName")) $("adminName").textContent = admin.username || "Super agent";

  $("agentSearch")?.addEventListener("input", renderAgents);
  $("refreshBtn")?.addEventListener("click", refreshDashboard);
  $("signOutBtn")?.addEventListener("click", () => Auth.logout());

  $("rebalanceRequestRows")?.addEventListener("click", event => {
    const approveButton = event.target.closest("[data-approve]");
    if (approveButton) approveRebalance(approveButton.dataset.approve);
    const passButton = event.target.closest("[data-pass]");
    if (passButton) passRebalance(passButton.dataset.pass);
  });

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

  refreshDashboard();
  window.setInterval(() => {
    if (!document.hidden) refreshDashboard();
  }, 10000);
});
