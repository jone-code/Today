#!/usr/bin/env node
// Read-only post-deploy check. It never prints tokens or response bodies.
import assert from "node:assert/strict";

const base = (process.env.TODAY_OPS_API_URL || "http://127.0.0.1:3001").replace(/\/$/, "");
const token = process.env.TODAY_METRICS_ADMIN_TOKEN?.trim();
const failOnWarnings = process.env.TODAY_OPS_FAIL_ON_WARNINGS === "true";
const timeoutMs = Number(process.env.TODAY_OPS_TIMEOUT_MS || 10_000);
if (!Number.isInteger(timeoutMs) || timeoutMs < 500 || timeoutMs > 60_000) {
  throw new Error("TODAY_OPS_TIMEOUT_MS must be an integer between 500 and 60000");
}
if (!token) throw new Error("TODAY_METRICS_ADMIN_TOKEN is required for the ops readiness check");

async function get(path, headers = {}) {
  const started = Date.now();
  const response = await fetch(`${base}${path}`, {
    headers,
    signal: AbortSignal.timeout(timeoutMs),
  });
  const elapsedMs = Date.now() - started;
  const text = await response.text();
  let body;
  try { body = JSON.parse(text); } catch { body = null; }
  return { response, body, elapsedMs };
}

const live = await get("/health/live");
assert.equal(live.response.status, 200, "liveness probe must return 200");
assert.equal(live.body?.ok, true, "liveness probe must report ok");

const ready = await get("/health/ready");
assert.equal(ready.response.status, 200, "readiness probe must return 200");
assert.equal(ready.body?.ok, true, "readiness probe must report ok");
assert.equal(ready.body?.database, true, "database readiness must be true");
assert.equal(ready.body?.vector, true, "vector readiness must be true");

const now = new Date();
const to = now.toISOString().slice(0, 10);
now.setUTCDate(now.getUTCDate() - 6);
const from = now.toISOString().slice(0, 10);
const auth = { "X-Today-Admin-Token": token };
const suffix = `?from=${from}&to=${to}`;
const metricPaths = [
  ["/v1/admin/metrics/activation", "funnel"],
  ["/v1/admin/metrics/retention", "cohort"],
  ["/v1/admin/metrics/quality", "events"],
];
const metricChecks = [];
let qualityBody;
let activationBody;
for (const [path, field] of metricPaths) {
  const result = await get(`${path}${suffix}`, auth);
  assert.equal(result.response.status, 200, `${path} must return 200`);
  assert.equal(result.body?.from, from);
  assert.equal(result.body?.to, to);
  assert.ok(result.body?.[field] !== undefined, `${path} must include ${field}`);
  const serialized = JSON.stringify(result.body);
  assert.equal(serialized.includes("rawText"), false, `${path} must not expose raw record text`);
  assert.equal(serialized.includes("transcript"), false, `${path} must not expose voice transcript text`);
  metricChecks.push({ path, elapsedMs: result.elapsedMs });
  if (path.endsWith("/activation")) activationBody = result.body;
  if (path.endsWith("/quality")) qualityBody = result.body;
}

const denied = await get(`/v1/admin/metrics/activation${suffix}`, {
  "X-Today-Admin-Token": `${token}-invalid`,
});
assert.equal(denied.response.status, 403, "invalid metrics token must be rejected");

const filtered = await get(`/v1/admin/metrics/quality${suffix}&platform=android&appVersion=0.1.0&eventSchemaVersion=1`, auth);
assert.equal(filtered.response.status, 200, "metric dimension filters must be accepted");
for (const event of Array.isArray(filtered.body?.events) ? filtered.body.events : []) {
  assert.equal(event.platform, "android", "filtered metric platform must match");
  assert.equal(event.appVersion, "0.1.0", "filtered metric version must match");
  assert.equal(event.eventSchemaVersion, 1, "filtered metric schema version must match");
}
const invalidFilter = await get(`/v1/admin/metrics/quality${suffix}&platform=desktop`, auth);
assert.equal(invalidFilter.response.status, 400, "unsupported metric platform must be rejected");

const alerts = [];
const addAlert = (code, message, value, threshold) => alerts.push({ code, severity: "warning", message, value, threshold });
const checkinsSaved = Number(activationBody?.funnel?.checkinsSaved || 0);
const checkinSavedEvents = Number(activationBody?.funnel?.checkinSavedEvents || 0);
const checkinEventDelta = checkinSavedEvents - checkinsSaved;
const checkinUsersMissingSavedEvent = Number(activationBody?.funnel?.checkinUsersMissingSavedEvent || 0);
const savedEventUsersMissingCheckin = Number(activationBody?.funnel?.savedEventUsersMissingCheckin || 0);
if (checkinEventDelta !== 0 || checkinUsersMissingSavedEvent > 0 || savedEventUsersMissingCheckin > 0) {
  addAlert("checkin_event_reconciliation", "saved check-ins and client save events have user-level gaps", {
    eventDelta: checkinEventDelta,
    checkinUsersMissingSavedEvent,
    savedEventUsersMissingCheckin,
  }, 0);
}
const voiceRows = Array.isArray(qualityBody?.voiceTranscriptions) ? qualityBody.voiceTranscriptions : [];
const voiceTotal = voiceRows.reduce((sum, row) => sum + Number(row.taskCount || 0), 0);
const voiceSucceeded = voiceRows
  .filter(row => row.status === "succeeded")
  .reduce((sum, row) => sum + Number(row.taskCount || 0), 0);
if (voiceTotal > 0 && voiceSucceeded / voiceTotal < 0.95) {
  addAlert("voice_success_rate", "voice transcription success rate is below 95%", voiceSucceeded / voiceTotal, 0.95);
}
const requestRows = Array.isArray(qualityBody?.dataRequests) ? qualityBody.dataRequests : [];
const requestTotal = requestRows.reduce((sum, row) => sum + Number(row.taskCount || 0), 0);
const requestFailed = requestRows
  .filter(row => row.status === "failed")
  .reduce((sum, row) => sum + Number(row.taskCount || 0), 0);
if (requestTotal > 0 && requestFailed / requestTotal > 0.05) {
  addAlert("data_request_failure_rate", "export or deletion failure rate is above 5%", requestFailed / requestTotal, 0.05);
}
if (alerts.length && failOnWarnings) {
  console.error(`Operational warning threshold exceeded (${alerts.map(alert => alert.code).join(", ")})`);
  process.exitCode = 2;
}

console.log(JSON.stringify({
  passed: true,
  base,
  dateRange: { from, to },
  probes: { liveMs: live.elapsedMs, readyMs: ready.elapsedMs },
  metrics: metricChecks,
  metricFilters: { accepted: true, invalidPlatformRejected: true, schemaVersionChecked: true },
  alerts,
}, null, 2));
