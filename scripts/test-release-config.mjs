#!/usr/bin/env node
import assert from "node:assert/strict";
import { spawn } from "node:child_process";

const base = {
  TODAY_INTERNAL_EMAIL_ALLOWLIST: "trial@example.com",
  TODAY_JWT_SECRET: "ci-release-jwt-secret-012345678901234567890123456789",
  TODAY_METRICS_ADMIN_TOKEN: "ci-release-metrics-token-012345678901234567890",
  TODAY_DATA_EXPORT_KEY: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  TODAY_PUBLIC_API_URL: "https://today.example.com",
  TODAY_VOICE_PER_HOUR: "20",
  TODAY_VOICE_PER_DAY: "100",
  TODAY_METRICS_AUDIT_RETAIN_DAYS: "180",
  TODAY_DATA_EXPORT_ENABLED: "true",
  TODAY_ACCOUNT_DELETE_ENABLED: "true",
};

async function run(overrides = {}) {
  const child = spawn(process.execPath, ["scripts/check-internal-release.mjs"], {
    cwd: process.cwd(),
    env: { ...process.env, ...base, ...overrides },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let stdout = ""; let stderr = "";
  child.stdout.on("data", chunk => { stdout += chunk; });
  child.stderr.on("data", chunk => { stderr += chunk; });
  const code = await new Promise(resolve => child.on("close", resolve));
  return { code, stdout, stderr };
}

let result = await run();
assert.equal(result.code, 0, result.stderr);
assert.match(result.stdout, /Internal release configuration passed/);

result = await run({ TODAY_JWT_SECRET: "short-secret" });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /JWT secret/);

result = await run({ TODAY_DATA_EXPORT_ENABLED: "enabled" });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /TODAY_DATA_EXPORT_ENABLED/);

result = await run({ TODAY_PUBLIC_API_URL: "http://today.example.com" });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /HTTPS/);

result = await run({ TODAY_METRICS_ADMIN_TOKEN: base.TODAY_DATA_EXPORT_KEY });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /independent from the export key/);

result = await run({ TODAY_JWT_SECRET: base.TODAY_DATA_EXPORT_KEY });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /independent from the export key/);

result = await run({ TODAY_METRICS_ADMIN_TOKEN: base.TODAY_JWT_SECRET });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /Metrics token must be an independent secret/);

result = await run({ TODAY_METRICS_AUDIT_RETAIN_DAYS: "0" });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /TODAY_METRICS_AUDIT_RETAIN_DAYS/);

result = await run({ TODAY_METRICS_AUDIT_RETAIN_DAYS: "3651" });
assert.notEqual(result.code, 0);
assert.match(result.stderr, /must not exceed 3650/);

console.log(JSON.stringify({ passed: true, scenarios: 9 }, null, 2));
