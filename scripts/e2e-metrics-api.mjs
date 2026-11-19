#!/usr/bin/env node
import assert from 'node:assert/strict';

const base = process.env.TODAY_E2E_API_URL || 'http://127.0.0.1:3001';
const token = process.env.TODAY_METRICS_ADMIN_TOKEN;
if (!token) throw new Error('Set TODAY_METRICS_ADMIN_TOKEN to run the metrics smoke check.');

const today = new Date();
const to = today.toISOString().slice(0, 10);
today.setUTCDate(today.getUTCDate() - 6);
const from = today.toISOString().slice(0, 10);

async function get(path, headers = {}) {
  const response = await fetch(`${base}${path}`, {
    headers: { 'X-Today-Admin-Token': token, ...headers },
    signal: AbortSignal.timeout(20_000),
  });
  const text = await response.text();
  let body;
  try { body = JSON.parse(text); } catch { body = text; }
  return { response, body };
}

const suffix = `?from=${from}&to=${to}`;
const endpoints = [
  ['/v1/admin/metrics/activation', 'funnel'],
  ['/v1/admin/metrics/retention', 'cohort'],
  ['/v1/admin/metrics/quality', 'events'],
];
const checks = [];
for (const [path, field] of endpoints) {
  const { response, body } = await get(path + suffix);
  assert.equal(response.status, 200, `${path} should return 200`);
  assert.equal(body.from, from);
  assert.equal(body.to, to);
  assert.ok(body[field], `${path} should include ${field}`);
  assert.equal(JSON.stringify(body).includes('rawText'), false, `${path} must not expose raw text`);
  if (path.endsWith('/activation')) {
    for (const key of ['checkinsSaved', 'checkinSavedEvents', 'checkinUsersMissingSavedEvent', 'savedEventUsersMissingCheckin']) {
      assert.equal(Number.isInteger(body.funnel?.[key]) && body.funnel[key] >= 0, true,
        `${path} ${key} must be a non-negative integer`);
    }
  }
  if (Array.isArray(body.events)) {
    for (const event of body.events) {
      assert.equal(typeof event.eventName, 'string', `${path} event name must be present`);
      if ('platform' in event) assert.equal(typeof event.platform, 'string', `${path} platform must be a string`);
      if ('appVersion' in event && event.appVersion !== null) assert.equal(typeof event.appVersion, 'string', `${path} app version must be a string or null`);
      if ('eventSchemaVersion' in event) assert.equal(Number.isInteger(event.eventSchemaVersion), true, `${path} schema version must be an integer`);
    }
  }
  checks.push(path);
}

const denied = await get(`/v1/admin/metrics/activation${suffix}`, { 'X-Today-Admin-Token': `${token}-wrong` });
assert.equal(denied.response.status, 403, 'invalid admin token should be rejected');

const filterSuffix = `${suffix}&platform=android&appVersion=0.1.0&eventSchemaVersion=1`;
const filtered = await get(`/v1/admin/metrics/quality${filterSuffix}`);
assert.equal(filtered.response.status, 200, 'metric dimension filters should return 200');
assert.deepEqual(filtered.body.filters, { platform: 'android', appVersion: '0.1.0', eventSchemaVersion: 1 });
assert.ok(Array.isArray(filtered.body.events), 'filtered quality response should include events');
for (const event of filtered.body.events) {
  assert.equal(event.platform, 'android', 'filtered event platform must match the request');
  assert.equal(event.appVersion, '0.1.0', 'filtered event version must match the request');
}

const invalidFilter = await get(`/v1/admin/metrics/quality${suffix}&platform=desktop`);
assert.equal(invalidFilter.response.status, 400, 'unsupported metric platform should be rejected');
console.log(JSON.stringify({
  passed: true,
  from,
  to,
  endpoints: checks,
  filters: { accepted: true, invalidPlatformRejected: true },
}, null, 2));
