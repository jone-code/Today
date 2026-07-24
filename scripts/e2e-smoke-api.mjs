#!/usr/bin/env node
/**
 * API smoke: register → login → checkin → wait summary → memories
 *
 * Env:
 *   API_BASE_URL (default http://127.0.0.1:3001)
 *   E2E_EMAIL / E2E_PASSWORD / E2E_DISPLAY_NAME (optional fixed account)
 */
const API_BASE = (process.env.API_BASE_URL || "http://127.0.0.1:3001").replace(
  /\/$/,
  "",
);

const stamp = Date.now();
const email =
  process.env.E2E_EMAIL || `e2e.smoke.${stamp}@example.com`;
const password = process.env.E2E_PASSWORD || "smoke-pass-123";
const displayName = process.env.E2E_DISPLAY_NAME || `Smoke${stamp % 100000}`;

function log(step, detail = "") {
  const suffix = detail ? ` — ${detail}` : "";
  console.log(`✓ ${step}${suffix}`);
}

async function request(path, { method = "GET", token, body } = {}) {
  const headers = { "content-type": "application/json" };
  if (token) headers.authorization = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!res.ok) {
    const msg =
      data && typeof data === "object" && data.message
        ? data.message
        : `${res.status} ${res.statusText}`;
    throw new Error(`${method} ${path} failed: ${msg}`);
  }
  return data;
}

async function waitForSummary(token, date, attempts = 40, intervalMs = 500) {
  let lastErr;
  for (let i = 1; i <= attempts; i++) {
    try {
      return await request(`/v1/summaries/${encodeURIComponent(date)}`, {
        token,
      });
    } catch (e) {
      lastErr = e;
      if (!String(e.message).includes("404")) throw e;
      await new Promise((r) => setTimeout(r, intervalMs));
    }
  }
  throw lastErr || new Error("summary not ready");
}

async function main() {
  console.log(`API smoke against ${API_BASE}`);

  const health = await request("/health");
  log("health", typeof health === "object" ? JSON.stringify(health) : String(health));

  let token;
  try {
    const registered = await request("/v1/auth/register", {
      method: "POST",
      body: { email, password, displayName },
    });
    token = registered.token;
    log("register", email);
  } catch (e) {
    if (!String(e.message).includes("already") && !String(e.message).includes("409")) {
      // fall through to login for pre-seeded accounts
    }
    const loggedIn = await request("/v1/auth/login", {
      method: "POST",
      body: { email, password },
    });
    token = loggedIn.token;
    log("login (existing)", email);
  }

  const me = await request("/v1/auth/me", { token });
  log("me", me.email || me.displayName);

  // Explicit login round-trip
  const loginAgain = await request("/v1/auth/login", {
    method: "POST",
    body: { email, password },
  });
  token = loginAgain.token;
  log("login");

  const rawText =
    "今天跑步锻炼了，还学了点 AI，工作压力有点大但整体还好。";
  const checkin = await request("/v1/checkins", {
    method: "POST",
    token,
    body: { rawText },
  });
  const date = checkin.checkin?.date || checkin.date;
  const status = checkin.status || "ready";
  log("checkin", `${date} status=${status}`);

  const summary =
    checkin.summary || (await waitForSummary(token, date));
  if (!summary?.oneLiner) {
    throw new Error("summary missing oneLiner");
  }
  log("summary", summary.oneLiner);

  const memories = await request("/v1/memories", { token });
  const items = memories.items || [];
  if (items.length < 1) {
    throw new Error("expected at least one memory after checkin");
  }
  log("memories", `${items.length} item(s); first=${items[0].text}`);

  // manage path briefly
  const first = items[0];
  const archived = await request(`/v1/memories/${encodeURIComponent(first.id)}/archive`, {
    method: "POST",
    token,
  });
  if (!archived.archived) throw new Error("archive failed");
  const active = await request("/v1/memories", { token });
  if ((active.items || []).some((m) => m.id === first.id)) {
    throw new Error("archived memory still in default list");
  }
  await request(`/v1/memories/${encodeURIComponent(first.id)}/unarchive`, {
    method: "POST",
    token,
  });
  log("memory archive/unarchive");

  console.log("\nAPI smoke passed.");
  console.log(
    JSON.stringify({ email, password, displayName, date, memoryCount: items.length }),
  );
}

main().catch((err) => {
  console.error("\nAPI smoke failed:", err.message || err);
  process.exit(1);
});
