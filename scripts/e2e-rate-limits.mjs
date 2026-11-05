#!/usr/bin/env node
// Run only against a disposable Today database with a low login-email test limit.
import { randomUUID } from "node:crypto";

const base = process.env.API_BASE_URL || "http://127.0.0.1:3106";
const maxAttempts = Number(process.env.TEST_LOGIN_EMAIL_LIMIT || 3);
const maxChats = Number(process.env.TEST_CHAT_HOURLY_LIMIT || 30);
const email = `rate.${randomUUID()}@example.com`;

const health = await fetch(`${base}/health`);
if (!health.ok || (await health.json()).service !== "today-api") {
  throw new Error("test API is unavailable");
}

const attempts = await Promise.all(Array.from({ length: maxAttempts + 9 }, () =>
  fetch(`${base}/v1/auth/login`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password: "incorrect-password" }),
  })));
const unauthorized = attempts.filter(response => response.status === 401);
const throttled = attempts.filter(response => response.status === 429);
if (unauthorized.length !== maxAttempts || throttled.length !== 9) {
  throw new Error(`expected ${maxAttempts} attempts and 9 throttles, got ${attempts.map(r => r.status)}`);
}
if (throttled.some(response => !Number(response.headers.get("retry-after")))) {
  throw new Error("429 response must specify Retry-After");
}
const other = await fetch(`${base}/v1/auth/login`, {
  method: "POST", headers: { "content-type": "application/json" },
  body: JSON.stringify({ email: `other.${email}`, password: "incorrect-password" }),
});
if (other.status !== 401) throw new Error("another account was affected by the email limit");

console.log(`concurrent login attempts limited to ${maxAttempts}; other account unaffected`);

const password = `Smoke-${randomUUID()}`;
const registered = await fetch(`${base}/v1/auth/register`, {
  method: "POST", headers: { "content-type": "application/json" },
  body: JSON.stringify({ email: `chat.${email}`, password, displayName: "Rate test" }),
});
if (!registered.ok) throw new Error(`registration failed: ${registered.status}`);
const { token } = await registered.json();
const headers = { "content-type": "application/json", authorization: `Bearer ${token}` };
const created = await fetch(`${base}/v1/agent/conversations`, {
  method: "POST", headers, body: JSON.stringify({}),
});
if (!created.ok) throw new Error(`conversation failed: ${created.status}`);
const { id } = await created.json();
const path = `${base}/v1/agent/conversations/${id}/messages`;
const clientMessageId = randomUUID();
const body = "TEST_AGENT_PROFILE";
async function send(text, messageId) {
  return fetch(path, { method: "POST", headers,
    body: JSON.stringify({ body: text, clientMessageId: messageId }) });
}

const initial = await send(body, clientMessageId);
if (!initial.ok) throw new Error(`first chat failed: ${initial.status}`);
const replyId = (await initial.json()).assistantMessage.id;
for (let i = 1; i < maxChats; i++) {
  const response = await send(body, randomUUID());
  if (!response.ok) throw new Error(`chat ${i + 1} failed: ${response.status}`);
}
const limited = await send(body, randomUUID());
if (limited.status !== 429 || !Number(limited.headers.get("retry-after"))) {
  throw new Error(`chat quota failed: ${limited.status}`);
}
const replay = await send(body, clientMessageId);
if (!replay.ok || (await replay.json()).assistantMessage.id !== replyId) {
  throw new Error("idempotent replay was blocked or repeated");
}
const safety = await send("我想伤害自己", randomUUID());
if (!safety.ok || (await safety.json()).provider !== "safety") {
  throw new Error("emergency reply was blocked by model quota");
}
const overdose = await send("我已经吃了很多安眠药", randomUUID());
if (!overdose.ok) throw new Error(`overdose safety reply was blocked: ${overdose.status}`);
const overdoseResult = await overdose.json();
if (overdoseResult.provider !== "safety" || overdoseResult.proposals.length !== 0) {
  throw new Error("explicit overdose must not call the model or propose actions");
}
console.log(`model quota limited to ${maxChats}; replay and emergency replies remain available`);
