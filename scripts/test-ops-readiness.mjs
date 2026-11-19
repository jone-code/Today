#!/usr/bin/env node
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";

let mismatch = false;
const metricTo = new Date().toISOString().slice(0, 10);
const metricFromDate = new Date(`${metricTo}T00:00:00.000Z`);
metricFromDate.setUTCDate(metricFromDate.getUTCDate() - 6);
const metricFrom = metricFromDate.toISOString().slice(0, 10);
const server = createServer((request, response) => {
  const requestUrl = new URL(request.url, "http://localhost");
  const path = requestUrl.pathname;
  const token = request.headers["x-today-admin-token"];
  if (path.startsWith("/v1/admin/metrics") && token !== "ops-token" && token !== "wrong-token") {
    response.writeHead(403); response.end(); return;
  }
  if (path.startsWith("/v1/admin/metrics") && token === "wrong-token") {
    response.writeHead(403, { "content-type": "application/json" }); response.end(JSON.stringify({ error: "forbidden" })); return;
  }
  if (path.startsWith("/v1/admin/metrics") && requestUrl.searchParams.get("platform") === "desktop") {
    response.writeHead(400, { "content-type": "application/json" }); response.end(JSON.stringify({ error: "unsupported platform" })); return;
  }
  let body;
  if (path === "/health/live" || path === "/health/ready") body = { ok: true, database: true, vector: true };
  else if (path.endsWith("/activation")) body = {
    from: metricFrom, to: metricTo,
    funnel: {
      checkinsSaved: 3,
      checkinSavedEvents: 3,
      checkinUsersMissingSavedEvent: mismatch ? 1 : 0,
      savedEventUsersMissingCheckin: 0,
    },
  };
  else if (path.endsWith("/retention")) body = { from: metricFrom, to: metricTo, cohort: { d1: 1, d3: 1, d7: 1 } };
  else if (path.endsWith("/quality")) body = {
    from: metricFrom, to: metricTo, events: [], dataRequests: [], voiceTranscriptions: [], feedback: [],
  };
  else { response.writeHead(404); response.end(); return; }
  response.writeHead(200, { "content-type": "application/json" }); response.end(JSON.stringify(body));
});

await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
const address = server.address();
const base = `http://127.0.0.1:${address.port}`;

async function run(failOnWarnings) {
  const child = spawn(process.execPath, ["scripts/check-ops-readiness.mjs"], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      TODAY_OPS_API_URL: base,
      TODAY_METRICS_ADMIN_TOKEN: "ops-token",
      TODAY_OPS_FAIL_ON_WARNINGS: failOnWarnings ? "true" : "false",
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let stdout = ""; let stderr = "";
  child.stdout.on("data", chunk => { stdout += chunk; });
  child.stderr.on("data", chunk => { stderr += chunk; });
  const code = await new Promise(resolve => child.on("close", resolve));
  return { code, stdout, stderr };
}

let result = await run(false);
assert.equal(result.code, 0, result.stderr);
assert.deepEqual(JSON.parse(result.stdout).alerts, []);

mismatch = true;
result = await run(true);
assert.equal(result.code, 2, result.stderr);
assert.match(result.stderr, /checkin_event_reconciliation/);
server.close();
console.log(JSON.stringify({ passed: true, reconciliationWarningBlocks: true }, null, 2));
