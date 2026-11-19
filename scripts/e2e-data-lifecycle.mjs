#!/usr/bin/env node
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';

const base = process.env.TODAY_E2E_API_URL || 'http://127.0.0.1:3001';
const email = process.env.TODAY_E2E_LIFECYCLE_EMAIL;
const password = process.env.TODAY_E2E_LIFECYCLE_PASSWORD;
if (!email || !password) throw new Error('Set TODAY_E2E_LIFECYCLE_EMAIL and TODAY_E2E_LIFECYCLE_PASSWORD to a NEW allowlisted test account.');
let token, created = false, deletion;
const evidence = [];
async function call(path, options = {}) {
  const response = await fetch(base + path, {
    ...options, signal: AbortSignal.timeout(20000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers },
  });
  const text = await response.text();
  let body; try { body = JSON.parse(text); } catch { body = text; }
  return { status: response.status, body };
}
const post = (path, body, headers) => call(path, { method: 'POST', body: JSON.stringify(body), headers });
try {
  const registration = await post('/v1/auth/register', { email, password, displayName: 'Lifecycle test' });
  // Never delete or modify a pre-existing account, including a previous test account.
  assert.equal(registration.status, 200, 'Fresh registration required; refusing to use an existing account.');
  token = registration.body.accessToken || registration.body.token; created = true;
  const privacy = await call('/v1/profile/privacy'); assert.equal(privacy.status, 200);
  const change = { ...privacy.body, chatMemoryEnabled: false };
  const patch = () => call('/v1/profile/privacy', { method: 'PATCH', body: JSON.stringify(change) });
  assert.equal((await patch()).status, 200); assert.equal((await patch()).status, 409);
  evidence.push('privacy version conflicts rejected');
  const unownedMemory = crypto.randomUUID();
  assert.equal((await call(`/v1/memories/${unownedMemory}/origins`)).status, 404);
  assert.equal((await call(`/v1/memories/${unownedMemory}/access-audit`)).status, 404);
  assert.equal((await call(`/v1/memories/${unownedMemory}/context-scope`, {
    method: 'PATCH', body: JSON.stringify({ scope: 'disabled' }),
  })).status, 404);
  evidence.push('unowned memory origins, audit and scope updates reveal no object');
  const record = await post('/v1/checkins', { rawText: 'Internal lifecycle verification record.' }, { 'Idempotency-Key': crypto.randomUUID() });
  assert.equal(record.status, 200);
  const key = crypto.randomUUID();
  const exported = await post('/v1/data-requests/export', {}, { 'Idempotency-Key': key }); assert.equal(exported.status, 200);
  assert.equal((await post('/v1/data-requests/export', {}, { 'Idempotency-Key': key })).body.id, exported.body.id);
  let task;
  for (let i = 0; i < 30; i++) {
    task = await call(`/v1/data-requests/${exported.body.id}`);
    if (task.body.status === 'succeeded' || task.body.status === 'failed') break;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  assert.equal(task.body.status, 'succeeded');
  const download = await post(`/v1/data-requests/${exported.body.id}/download-token`, {});
  const path = `/v1/data-requests/${exported.body.id}/download?token=${encodeURIComponent(download.body.token)}`;
  const zip = await fetch(base + path, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(20000) });
  assert.equal(zip.status, 200);
  const bytes = Buffer.from(await zip.arrayBuffer()); assert.equal(bytes.subarray(0,2).toString(), 'PK');
  assert.equal((await call(path)).status, 404);
  if (process.env.TODAY_E2E_EXPORT_PATH) await writeFile(process.env.TODAY_E2E_EXPORT_PATH, bytes);
  evidence.push('idempotent export and single-use ZIP download passed');
  assert.equal((await post('/v1/data-requests/delete-account', { phrase: 'incorrect' })).status, 400);
  deletion = await post('/v1/data-requests/delete-account', { phrase: 'DELETE MY ACCOUNT' }); assert.equal(deletion.status, 200);
  assert.equal((await post('/v1/data-requests/delete-account', { phrase: 'DELETE MY ACCOUNT' })).body.id, deletion.body.id);
  assert.equal((await call('/v1/today/home')).status, 403);
  for (let i = 0; i < 40; i++) {
    const state = await call(`/v1/data-requests/${deletion.body.id}`);
    if (state.status === 401 || state.body.status === 'succeeded') break;
    assert.notEqual(state.body.status, 'failed', 'Deletion requires operator review');
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  assert.equal((await call('/v1/auth/me')).status, 401);
  assert.equal((await post('/v1/auth/login', { email, password })).status, 401);
  evidence.push('durable deletion blocked ordinary access and invalidated credentials');
  console.log(JSON.stringify({ passed: true, checks: evidence }, null, 2));
} finally {
  if (created && !deletion) await post('/v1/data-requests/delete-account', { phrase: 'DELETE MY ACCOUNT' }).catch(() => {});
}
