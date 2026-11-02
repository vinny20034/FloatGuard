/*
 * FLOATGUARD - SHARED AUTH CLIENT
 *
 * Stores the opaque bearer token and the signed-in user in localStorage, and
 * exposes an authenticated fetch wrapper plus page guards. Loaded by every page
 * (index.html, agent.html, admin.html) before the page-specific script.
 */

const AUTH_TOKEN_KEY = "floatguard_token_v1";
const AUTH_USER_KEY = "floatguard_user_v1";

const Auth = (() => {
  function save(token, user) {
    localStorage.setItem(AUTH_TOKEN_KEY, token);
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
  }

  function token() {
    return localStorage.getItem(AUTH_TOKEN_KEY);
  }

  function user() {
    try {
      return JSON.parse(localStorage.getItem(AUTH_USER_KEY));
    } catch {
      return null;
    }
  }

  function clear() {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(AUTH_USER_KEY);
  }

  /** Landing page for a role. */
  function home(role) {
    return role === "ADMIN" ? "admin.html" : "agent.html";
  }

  /**
   * fetch wrapper that attaches the bearer token and normalises the JSON result
   * to { ok, status, data?, message? }. A 401 outside the auth endpoints means
   * the session died, so the user is sent back to the sign-in page.
   */
  async function api(method, path, body) {
    const headers = { "Content-Type": "application/json" };
    if (token()) headers.Authorization = "Bearer " + token();

    const options = { method, headers };
    if (body !== undefined) options.body = JSON.stringify(body);

    let response;
    try {
      response = await fetch(path, options);
    } catch {
      return { ok: false, status: 0, message: "Cannot reach the backend. Make sure the server is running." };
    }

    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = text; }
    }

    if (response.status === 401 && !path.startsWith("/auth/")) {
      clear();
      window.location.replace("index.html");
      return { ok: false, status: 401, message: "Your session has expired. Please sign in again." };
    }

    if (!response.ok) {
      const message = data && data.message ? data.message : `Request failed (${response.status}).`;
      return { ok: false, status: response.status, data, message };
    }
    return { ok: true, status: response.status, data };
  }

  async function logout() {
    try { await api("POST", "/auth/logout"); } catch { /* token may already be gone */ }
    clear();
    window.location.replace("index.html");
  }

  /**
   * Ensures a signed-in user before a protected page renders. Redirects to the
   * sign-in page when there is no session, and to the user's own home when the
   * role does not match. Returns the user, or null when a redirect was issued.
   */
  function guard(role) {
    const current = user();
    if (!token() || !current) {
      window.location.replace("index.html");
      return null;
    }
    if (role && current.role !== role) {
      window.location.replace(home(current.role));
      return null;
    }
    return current;
  }

  return { save, token, user, clear, home, api, logout, guard };
})();
