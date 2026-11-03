/*
 * FLOATGUARD - SIGN IN / REGISTER PAGE
 *
 * Talks to POST /auth/login and POST /auth/register, then stores the returned
 * session via auth.js and forwards the user to their role's home page.
 */

const loginMessage = document.getElementById("authMessage");

function showAuthMessage(message, type = "error") {
  loginMessage.textContent = message;
  loginMessage.className = `message ${type}`;
  loginMessage.style.display = "block";
}

function clearAuthMessage() {
  loginMessage.style.display = "none";
  loginMessage.className = "message";
}

function selectTab(tab) {
  document.querySelectorAll(".auth-tab").forEach(button => {
    button.classList.toggle("active", button.dataset.tab === tab);
  });
  document.getElementById("loginForm").hidden = tab !== "login";
  document.getElementById("registerForm").hidden = tab !== "register";
  clearAuthMessage();
}

function syncRoleFields() {
  const isAgent = document.getElementById("regRole").value === "AGENT";
  document.getElementById("adminFields").hidden = isAgent;
  document.querySelector('label[for="regAgentName"]').textContent =
    isAgent ? "Business name" : "Super-agent name";
}

async function onSignedIn(result) {
  Auth.save(result.token, {
    username: result.username,
    role: result.role,
    agentId: result.agentId
  });
  window.location.replace(Auth.home(result.role));
}

async function handleLogin(event) {
  event.preventDefault();
  clearAuthMessage();

  const username = document.getElementById("loginUsername").value.trim();
  const password = document.getElementById("loginPassword").value;

  const res = await Auth.api("POST", "/auth/login", { username, password });
  if (!res.ok) {
    showAuthMessage(res.message);
    return;
  }
  await onSignedIn(res.data);
}

async function handleRegister(event) {
  event.preventDefault();
  clearAuthMessage();

  const role = document.getElementById("regRole").value;
  const body = {
    username: document.getElementById("regUsername").value.trim(),
    password: document.getElementById("regPassword").value,
    role
  };

  body.agentName = document.getElementById("regAgentName").value.trim();
  body.location = document.getElementById("regLocation").value.trim();
  body.nrcNumber = document.getElementById("regNrcNumber").value.trim();
  body.phoneNumber = document.getElementById("regPhoneNumber").value.trim();
  body.openingCash = document.getElementById("regOpeningCash").valueAsNumber;
  body.openingEFloat = document.getElementById("regOpeningEFloat").valueAsNumber;
  if (role === "ADMIN") body.inviteCode = document.getElementById("regInviteCode").value.trim();

  const res = await Auth.api("POST", "/auth/register", body);
  if (!res.ok) {
    showAuthMessage(res.message);
    return;
  }
  await onSignedIn(res.data);
}

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".auth-tab").forEach(button => {
    button.addEventListener("click", () => selectTab(button.dataset.tab));
  });

  document.getElementById("loginForm").addEventListener("submit", handleLogin);
  document.getElementById("registerForm").addEventListener("submit", handleRegister);
  document.getElementById("regRole").addEventListener("change", syncRoleFields);

  syncRoleFields();
});
