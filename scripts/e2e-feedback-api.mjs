#!/usr/bin/env node
// Run only against a disposable Today database with TODAY_RATE_FEEDBACK_PER_DAY=2.
import { randomUUID } from "node:crypto";

const base = process.env.API_BASE_URL || "http://127.0.0.1:3103";
const password = `Smoke-${randomUUID()}`;

async function request(path, { method = "GET", token, body, key, status = 200 } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(key ? { "Idempotency-Key": key } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (response.status !== status) {
    throw new Error(`${method} ${path}: expected ${status}, got ${response.status} ${JSON.stringify(data)}`);
  }
  return { data, response };
}

function check(condition, message) { if (!condition) throw new Error(message); }

const email = `feedback.${randomUUID()}@example.com`;
const { data: owner } = await request("/v1/auth/register", { method: "POST",
  body: { email, password, displayName: "Feedback owner" } });
const { data: other } = await request("/v1/auth/register", { method: "POST",
  body: { email: `other.${email}`, password, displayName: "Other" } });
const token = owner.token;
const otherToken = other.token;
await request("/v1/feedback", { status: 401 });
await request("/v1/feedback", { method: "POST", token, body: { kind: "issue", body: "无法打开" }, status: 400 });
await request("/v1/feedback", { method: "POST", token, key: randomUUID(),
  body: { kind: "issue", body: "   " }, status: 400 });
await request("/v1/feedback", { method: "POST", token, key: randomUUID(),
  body: { kind: "invalid", body: "无法打开" }, status: 400 });

const key = randomUUID();
const issue = { kind: "issue", body: "无法打开对话" };
const { data: first } = await request("/v1/feedback", { method: "POST", token, key, body: issue });
check(first.body === issue.body && first.kind === "issue", "feedback body or kind changed");
check((await request("/v1/feedback", { method: "POST", token, key, body: issue })).data.id === first.id,
  "idempotent retry created another report");
await request("/v1/feedback", { method: "POST", token, key,
  body: { kind: "issue", body: "其他问题" }, status: 409 });
check((await request("/v1/feedback", { token })).data.length === 1, "unexpected owner reports");
check((await request("/v1/feedback", { token: otherToken })).data.length === 0,
  "feedback leaked to another user");
await request(`/v1/feedback/${first.id}`, { method: "DELETE", token: otherToken, status: 404 });
await request(`/v1/feedback/${first.id}`, { method: "DELETE", token, status: 204 });
await request(`/v1/feedback/${first.id}`, { method: "DELETE", token, status: 204 });
check((await request("/v1/feedback", { token })).data.length === 0, "deleted feedback is still visible");
await request("/v1/feedback", { method: "POST", token, key, body: issue, status: 409 });
check((await request("/v1/feedback", { token })).data.length === 0,
  "deleted feedback was recreated by a retry");

const { data: second } = await request("/v1/feedback", { method: "POST", token, key: randomUUID(),
  body: { kind: "idea", body: "希望可以搜索" } });
check(second.id !== first.id, "new submission reused the deleted report");
const limited = await request("/v1/feedback", { method: "POST", token, key: randomUUID(),
  body: { kind: "issue", body: "又一个问题" }, status: 429 });
check(Number(limited.response.headers.get("Retry-After")) > 0, "rate limit lacks Retry-After");
check((await request("/v1/feedback", { token })).data.length === 1, "rate-limited report was saved");
console.log("feedback auth, validation, ownership, idempotency, deletion and rate limit passed");
