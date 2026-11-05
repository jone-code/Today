#!/usr/bin/env node
// Run only against a disposable database with scripts/mock-agent-model.mjs.
import { randomUUID } from "node:crypto";

const base = process.env.API_BASE_URL || "http://127.0.0.1:3101";
const mock = process.env.MOCK_MODEL_BASE_URL || "http://127.0.0.1:39011";

async function request(path, token, options = {}) {
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
  });
  const data = await response.json().catch(() => null);
  return { status: response.status, data };
}

async function until(check, description, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await check();
    if (value) return value;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error(`timed out waiting for ${description}`);
}

const password = `Smoke-${randomUUID()}`;
const registered = await request("/v1/auth/register", null, {
  method: "POST", body: JSON.stringify({ email: `checkin.race.${randomUUID()}@example.com`, password, displayName: "Race test" }),
});
if (registered.status !== 200) throw new Error(`registration failed: ${JSON.stringify(registered)}`);
const token = registered.data.token;
const date = new Date().toISOString().slice(0, 10);
const before = await (await fetch(`${mock}/__delayed_summary_started`)).json();

const first = await request("/v1/checkins", token, {
  method: "POST", body: JSON.stringify({ date, rawText: "TEST_DELAY_SUMMARY_OLD" }),
});
if (first.status !== 200 || first.data.status !== "processing") throw new Error(`old checkin failed: ${JSON.stringify(first)}`);
await until(async () => {
  const response = await fetch(`${mock}/__delayed_summary_started`);
  return (await response.json()).count > before.count;
}, "old model request");

const second = await request("/v1/checkins", token, {
  method: "POST", body: JSON.stringify({ date, rawText: "TEST_DELAY_SUMMARY_NEW" }),
});
if (second.status !== 200 || second.data.status !== "processing") throw new Error(`new checkin failed: ${JSON.stringify(second)}`);
await until(async () => {
  const today = await request("/v1/checkins/today", token);
  const summary = await request(`/v1/summaries/${date}`, token);
  return today.data?.aiJob?.status === "succeeded" && summary.data?.oneLiner === "new summary";
}, "new summary and successful replacement job", 8000);
await new Promise(resolve => setTimeout(resolve, 1100));
const final = await request(`/v1/summaries/${date}`, token);
if (final.data?.oneLiner !== "new summary") throw new Error(`old summary reappeared: ${JSON.stringify(final)}`);
const finalJob = await request("/v1/checkins/today", token);
if (finalJob.data?.aiJob?.status !== "succeeded") {
  throw new Error(`old task changed replacement status: ${JSON.stringify(finalJob)}`);
}
console.log("late summary rejected; replacement summary and job state preserved");
