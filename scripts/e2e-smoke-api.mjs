#!/usr/bin/env node
/**
 * API smoke: register → login → checkin → candidate → confirm → memory
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

async function waitForCandidates(token, checkinId, attempts = 40, intervalMs = 500) {
  for (let i = 1; i <= attempts; i++) {
    const candidates = await request(
      `/v1/checkins/${encodeURIComponent(checkinId)}/memory-candidates`,
      { token },
    );
    if (candidates.some((candidate) => candidate.status === "pending")) {
      return candidates;
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error("expected at least one pending memory candidate after checkin");
}

async function main() {
  console.log(`API smoke against ${API_BASE}`);

  const health = await request("/health");
  if (health?.service !== "today-api") {
    throw new Error(`unexpected service at ${API_BASE}: ${JSON.stringify(health)}`);
  }
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
    "我每周坚持跑步，今天也跑步锻炼了，还学了点 AI，工作压力有点大但整体还好。";
  const checkin = await request("/v1/checkins", {
    method: "POST",
    token,
    body: { rawText },
  });
  const date = checkin.checkin?.date || checkin.date;
  const status = checkin.status || "ready";
  log("checkin", `${date} status=${status}`);
  const checkinById = await request(`/v1/checkins/${encodeURIComponent(checkin.checkin.id)}`, { token });
  if (checkinById.rawText !== rawText || checkinById.id !== checkin.checkin.id) {
    throw new Error("checkin detail did not return the original text");
  }
  log("checkin detail");

  const summary =
    checkin.summary || (await waitForSummary(token, date));
  if (!summary?.oneLiner) {
    throw new Error("summary missing oneLiner");
  }
  log("summary", summary.oneLiner);

  const beforeConfirm = await request("/v1/memories", { token });
  if ((beforeConfirm.items || []).length !== 0) {
    throw new Error("checkin wrote long-term memory before user confirmation");
  }
  const candidates = await waitForCandidates(token, checkin.checkin.id);
  const candidate = candidates.find((item) => item.status === "pending");
  const correctedText = "我每周跑步，正在坚持这项习惯。";
  const confirmed = await request(
    `/v1/checkins/memory-candidates/${encodeURIComponent(candidate.id)}/confirm`,
    { method: "POST", token, body: { text: correctedText } },
  );
  if (confirmed.text !== correctedText) throw new Error("confirmed candidate did not retain corrected text");
  const afterConfirm = await request("/v1/memories", { token });
  const items = afterConfirm.items || [];
  if (!items.some((item) => item.text === correctedText) || items.some((item) => item.text === candidate.text)) {
    throw new Error("memory must contain only the corrected candidate text");
  }
  log("memory confirmation", correctedText);

  // manage path briefly
  const first = items[0];
  const originsPath = `/v1/memories/${encodeURIComponent(first.id)}/origins`;
  const origins = await request(originsPath, { token });
  if (!origins.some((origin) => origin.kind === "checkin"
    && origin.sourceId === checkin.checkin.id && origin.date === date)) {
    throw new Error("confirmed memory is missing its active checkin origin");
  }
  if (origins.some((origin) => "rawText" in origin)) {
    throw new Error("memory origin response exposed original text");
  }
  const other = await request("/v1/auth/register", {
    method: "POST", body: { email: `e2e.other.${stamp}@example.com`, password, displayName: "Other" },
  });
  for (const path of [originsPath, `/v1/checkins/${encodeURIComponent(checkin.checkin.id)}`]) {
    const response = await fetch(`${API_BASE}${path}`, { headers: { authorization: `Bearer ${other.token}` } });
    if (response.status !== 404) throw new Error(`another user accessed ${path}: ${response.status}`);
  }
  log("memory origin and checkin ownership");
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

  await request("/v1/checkins", {
    method: "POST", token, body: { date, rawText: "今天的记录已更正，不再包含此前的内容。" },
  });
  const correctedMemories = await request("/v1/memories?includeArchived=true", { token });
  if (correctedMemories.items?.some((memory) => memory.id === first.id)) {
    throw new Error("memory derived from an older checkin revision remains visible");
  }
  const correctedOrigins = await request(originsPath, { token }).catch((error) => {
    if (String(error.message).includes("404")) return [];
    throw error;
  });
  if (correctedOrigins.length) throw new Error("invalidated memory still exposes an old source");
  log("checkin correction revokes its old memory source");

  const yesterday = new Date(Date.parse(`${date}T00:00:00Z`) - 86400000)
    .toISOString().slice(0, 10);
  await request("/v1/checkins", {
    method: "POST", token, body: { date: yesterday, rawText: "昨天参加了面试，想明天看看结果。" },
  });
  const opening = await request("/v1/proactive/today", { token });
  if (opening.prompts?.some((prompt) => prompt.source === "followup")) {
    throw new Error("legacy daily prompt bypassed Agent followup authorization");
  }
  log("legacy daily opening cannot create unconsented followups");

  console.log("\nAPI smoke passed.");
  console.log(
    JSON.stringify({ email, date, memoryCount: items.length }),
  );
}

main().catch((err) => {
  console.error("\nAPI smoke failed:", err.message || err);
  process.exit(1);
});
