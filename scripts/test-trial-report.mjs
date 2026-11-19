#!/usr/bin/env node
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";

const payloads = {
  "/v1/admin/metrics/activation": {
    from: "2026-11-01", to: "2026-11-07",
    funnel: {
      registeredUsers: 4, activatedUsers: 4, chatUsers: 3, valueUsers: 3,
      northStarUsers: 2, checkinsSaved: 8, checkinSavedEvents: 8,
      checkinUsersMissingSavedEvent: 0, savedEventUsersMissingCheckin: 0,
    },
  },
  "/v1/admin/metrics/retention": {
    from: "2026-11-01", to: "2026-11-07", cohort: { d1: 3, d3: 2, d7: 1 },
  },
  "/v1/admin/metrics/quality": {
    from: "2026-11-01", to: "2026-11-07",
    dataRequests: [{ type: "export", status: "succeeded", taskCount: 2 }],
    voiceTranscriptions: [{ status: "failed", errorCode: "provider_unconfigured", taskCount: 2 }],
    feedback: [
      { kind: "idea", feedbackCount: 3, userCount: 2 },
      { kind: "bug", feedbackCount: 1, userCount: 1 },
    ],
    events: [{ eventName: "checkin_saved", platform: "web", appVersion: "0.1.0", eventSchemaVersion: 1, eventCount: 4, userCount: 3 }],
  },
};
let mode = "healthy";

const server = createServer((request, response) => {
  const path = new URL(request.url, "http://localhost").pathname;
  const baseBody = payloads[path];
  const body = mode === "mismatch" && path.endsWith("/activation")
    ? { ...baseBody, funnel: { ...baseBody.funnel, checkinUsersMissingSavedEvent: 1, savedEventUsersMissingCheckin: 0 } }
    : mode === "failed" && path.endsWith("/quality")
    ? { ...baseBody, dataRequests: [{ type: "export", status: "failed", taskCount: 1 }] }
    : baseBody;
  if (!body || request.headers["x-today-admin-token"] !== "test-token") {
    response.writeHead(403, { "content-type": "application/json" });
    response.end(JSON.stringify({ error: "forbidden" }));
    return;
  }
  response.writeHead(200, { "content-type": "application/json" });
  response.end(JSON.stringify(body));
});

await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
const address = server.address();
const base = `http://127.0.0.1:${address.port}`;
async function runReport() {
  const child = spawn(process.execPath, ["scripts/build-trial-report.mjs"], {
  cwd: process.cwd(),
  env: {
    ...process.env,
    TODAY_TRIAL_API_URL: base,
    TODAY_METRICS_ADMIN_TOKEN: "test-token",
    TODAY_TRIAL_FROM: "2026-11-01",
    TODAY_TRIAL_TO: "2026-11-07",
  },
  stdio: ["ignore", "pipe", "pipe"],
  });
  let stdout = "";
  let stderr = "";
  child.stdout.on("data", chunk => { stdout += chunk; });
  child.stderr.on("data", chunk => { stderr += chunk; });
  const exitCode = await new Promise(resolve => child.on("close", resolve));
  assert.equal(exitCode, 0, stderr);
  return JSON.parse(stdout);
}

const report = await runReport();
assert.equal(report.reportVersion, 1);
assert.equal(report.recommendation, "继续投入");
assert.equal(report.quality.feedbackCount, 4);
assert.equal(report.quality.feedbackUserCount, 3);
assert.deepEqual(report.quality.feedbackUsersByKind, { idea: 2, bug: 1 });
assert.deepEqual(report.quality.eventDimensions, [{
  eventName: "checkin_saved", platform: "web", appVersion: "0.1.0",
  eventSchemaVersion: 1, eventCount: 4, userCount: 3,
}]);
assert.deepEqual(report.reconciliation, {
  checkinsSaved: 8,
  checkinSavedEvents: 8,
  checkinUsersMissingSavedEvent: 0,
  savedEventUsersMissingCheckin: 0,
  checkinEventDelta: 0,
  consistent: true,
});
mode = "mismatch";
const mismatchReport = await runReport();
assert.equal(mismatchReport.recommendation, "调整方案");
assert.equal(mismatchReport.reconciliation.consistent, false);
assert.equal(mismatchReport.reconciliation.checkinUsersMissingSavedEvent, 1);
mode = "failed";
const failedReport = await runReport();
assert.equal(failedReport.recommendation, "暂停开放");
assert.equal(failedReport.quality.dataRequestFailureRate, 1);
server.close();
console.log(JSON.stringify({ passed: true, recommendations: [report.recommendation, failedReport.recommendation] }, null, 2));
