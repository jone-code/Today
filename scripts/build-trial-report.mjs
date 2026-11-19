#!/usr/bin/env node
// Build a redacted internal-trial decision report from aggregate endpoints.
import fs from "node:fs/promises";

const base = (process.env.TODAY_TRIAL_API_URL || "http://127.0.0.1:3001").replace(/\/$/, "");
const token = process.env.TODAY_METRICS_ADMIN_TOKEN?.trim();
if (!token) throw new Error("TODAY_METRICS_ADMIN_TOKEN is required");
const to = process.env.TODAY_TRIAL_TO || new Date().toISOString().slice(0, 10);
const datePattern = /^\d{4}-\d{2}-\d{2}$/;
const validDate = value => datePattern.test(value) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
if (!validDate(to)) throw new Error("TODAY_TRIAL_TO must be YYYY-MM-DD");
const fromDate = new Date(`${to}T00:00:00Z`);
fromDate.setUTCDate(fromDate.getUTCDate() - 6);
const from = process.env.TODAY_TRIAL_FROM || fromDate.toISOString().slice(0, 10);
if (!validDate(from) || from > to) throw new Error("TODAY_TRIAL_FROM must be a valid date no later than TODAY_TRIAL_TO");
const suffix = `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;

async function get(path) {
  const response = await fetch(`${base}${path}${suffix}`, {
    headers: { "X-Today-Admin-Token": token },
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`${path} returned ${response.status}`);
  return response.json();
}

const [activation, retention, quality] = await Promise.all([
  get("/v1/admin/metrics/activation"),
  get("/v1/admin/metrics/retention"),
  get("/v1/admin/metrics/quality"),
]);
const funnel = activation.funnel || {};
const registered = Number(funnel.registeredUsers || 0);
const rate = (value) => registered > 0 ? Number(value || 0) / registered : null;
const rowsTotal = rows => (Array.isArray(rows) ? rows : []).reduce((sum, row) => sum + Number(row.taskCount || 0), 0);
const rowsCount = (rows, predicate) => (Array.isArray(rows) ? rows : [])
  .filter(predicate).reduce((sum, row) => sum + Number(row.taskCount || 0), 0);
const voiceRows = quality.voiceTranscriptions || [];
const voiceTotal = rowsTotal(voiceRows);
const voiceUnconfiguredOnly = voiceTotal > 0 && voiceRows.every(row => row.errorCode === "provider_unconfigured");
const voiceSuccessRate = voiceTotal > 0 ? rowsCount(voiceRows, row => row.status === "succeeded") / voiceTotal : null;
const requests = quality.dataRequests || [];
const requestTotal = rowsTotal(requests);
const requestFailureRate = requestTotal > 0 ? rowsCount(requests, row => row.status === "failed") / requestTotal : 0;
const feedbackRows = Array.isArray(quality.feedback) ? quality.feedback : [];
const feedbackCount = rowsTotal(feedbackRows.map(row => ({ taskCount: row.feedbackCount })));
const feedbackUsersByKind = Object.fromEntries(feedbackRows.map(row => [
  String(row.kind || "unknown"), Number(row.userCount || 0),
]));
const feedbackUserCount = Object.values(feedbackUsersByKind).reduce((sum, count) => sum + count, 0);
const eventDimensions = (Array.isArray(quality.events) ? quality.events : []).map(row => ({
  eventName: String(row.eventName || "unknown"),
  platform: row.platform == null ? null : String(row.platform),
  appVersion: row.appVersion == null ? null : String(row.appVersion),
  eventSchemaVersion: row.eventSchemaVersion == null ? null : Number(row.eventSchemaVersion),
  eventCount: Number(row.eventCount || 0),
  userCount: Number(row.userCount || 0),
}));

const reasons = [];
if (requestFailureRate > 0.05) reasons.push("导出或删除任务失败率超过 5%");
if (rate(funnel.activatedUsers) !== null && rate(funnel.activatedUsers) < 0.70) reasons.push("首条记录激活率低于 70%");
if (rate(retention.cohort?.d7) !== null && rate(retention.cohort?.d7) < 0.25) reasons.push("D7 留存低于 25%");
if (voiceSuccessRate !== null && voiceSuccessRate < 0.95 && !voiceUnconfiguredOnly) reasons.push("语音转写成功率低于 95%");
const checkinsSaved = Number(funnel.checkinsSaved || 0);
const checkinSavedEvents = Number(funnel.checkinSavedEvents || 0);
const checkinEventDelta = checkinSavedEvents - checkinsSaved;
const checkinUsersMissingSavedEvent = Number(funnel.checkinUsersMissingSavedEvent || 0);
const savedEventUsersMissingCheckin = Number(funnel.savedEventUsersMissingCheckin || 0);
if (checkinEventDelta !== 0 || checkinUsersMissingSavedEvent > 0 || savedEventUsersMissingCheckin > 0) {
  reasons.push("记录业务事实与客户端事件存在用户级差异");
}
const recommendation = requestFailureRate > 0.05 ? "暂停开放" : reasons.length ? "调整方案" : "继续投入";

const report = {
  reportVersion: 1,
  generatedAt: new Date().toISOString(),
  dateRange: { from, to },
  recommendation,
  reasons,
  activation: {
    registeredUsers: registered,
    activatedUsers: Number(funnel.activatedUsers || 0),
    activationRate: rate(funnel.activatedUsers),
    northStarUsers: Number(funnel.northStarUsers || 0),
    checkinsSaved,
    checkinSavedEvents,
    checkinEventDelta,
    checkinUsersMissingSavedEvent,
    savedEventUsersMissingCheckin,
  },
  retention: {
    d1Users: Number(retention.cohort?.d1 || 0),
    d3Users: Number(retention.cohort?.d3 || 0),
    d7Users: Number(retention.cohort?.d7 || 0),
    d7Rate: rate(retention.cohort?.d7),
  },
  quality: {
    voiceTasks: voiceTotal,
    voiceSuccessRate,
    voiceProviderConfigured: !voiceUnconfiguredOnly,
    dataRequestTasks: requestTotal,
    dataRequestFailureRate: requestFailureRate,
    feedbackCount,
    feedbackUserCount,
    feedbackUsersByKind,
    eventDimensions,
  },
  reconciliation: {
    checkinsSaved,
    checkinSavedEvents,
    checkinEventDelta,
    checkinUsersMissingSavedEvent,
    savedEventUsersMissingCheckin,
    consistent: checkinEventDelta === 0
      && checkinUsersMissingSavedEvent === 0
      && savedEventUsersMissingCheckin === 0,
  },
};

const output = process.env.TODAY_TRIAL_REPORT_OUT;
if (output) await fs.writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600, flag: "wx" });
else console.log(JSON.stringify(report, null, 2));
