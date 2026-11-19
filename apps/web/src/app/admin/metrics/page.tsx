"use client";

import { useState } from "react";
import Link from "next/link";
import {
  ActivationMetricsSchema,
  QualityMetricsSchema,
  RetentionMetricsSchema,
  type ActivationMetrics,
  type QualityMetrics,
  type RetentionMetrics,
} from "@today/contracts";

type MetricState = {
  activation: ActivationMetrics;
  retention: RetentionMetrics;
  quality: QualityMetrics;
};

function isoDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function defaultRange() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - 30);
  return { from: isoDate(from), to: isoDate(to) };
}

export default function MetricsPage() {
  const [initial] = useState(() => defaultRange());
  const [token, setToken] = useState("");
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [platform, setPlatform] = useState("");
  const [appVersion, setAppVersion] = useState("");
  const [eventSchemaVersion, setEventSchemaVersion] = useState("");
  const [metrics, setMetrics] = useState<MetricState | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const eventDelta = metrics
    ? metrics.activation.funnel.checkinSavedEvents - metrics.activation.funnel.checkinsSaved
    : 0;
  const checkinUsersMissingSavedEvent = metrics?.activation.funnel.checkinUsersMissingSavedEvent ?? 0;
  const savedEventUsersMissingCheckin = metrics?.activation.funnel.savedEventUsersMissingCheckin ?? 0;
  const reconciliationConsistent = eventDelta === 0
    && checkinUsersMissingSavedEvent === 0
    && savedEventUsersMissingCheckin === 0;

  async function load() {
    if (!token.trim() || !from || !to) return;
    setBusy(true);
    setError("");
    try {
      const params = new URLSearchParams({ from, to });
      if (platform) params.set("platform", platform);
      if (appVersion.trim()) params.set("appVersion", appVersion.trim());
      if (eventSchemaVersion) params.set("eventSchemaVersion", eventSchemaVersion);
      const suffix = `?${params.toString()}`;
      const headers = { "X-Today-Admin-Token": token.trim() };
      const requestInit = { headers, cache: "no-store" as RequestCache };
      const responses = await Promise.all([
        fetch(`/api/v1/admin/metrics/activation${suffix}`, requestInit),
        fetch(`/api/v1/admin/metrics/retention${suffix}`, requestInit),
        fetch(`/api/v1/admin/metrics/quality${suffix}`, requestInit),
      ]);
      if (responses.some((response) => !response.ok)) {
        throw new Error("指标读取失败，请检查管理员令牌和日期范围。");
      }
      const [activation, retention, quality] = await Promise.all(
        responses.map((response) => response.json()),
      );
      setMetrics({
        activation: ActivationMetricsSchema.parse(activation),
        retention: RetentionMetricsSchema.parse(retention),
        quality: QualityMetricsSchema.parse(quality),
      });
    } catch (cause) {
      setMetrics(null);
      setError(cause instanceof Error ? cause.message : "指标响应格式无效。");
    } finally {
      setBusy(false);
    }
  }

  function downloadMetrics() {
    if (!metrics) return;
    const blob = new Blob([JSON.stringify({ from, to, ...metrics }, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `today-metrics-${from}-${to}.json`;
    anchor.style.display = "none";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  return (
    <main className="metrics-page">
      <div className="metrics-heading">
        <div>
          <p className="eyebrow">内部试用 · 聚合数据</p>
          <h1>Today 指标看板</h1>
          <p>只显示计数，不展示用户记录正文或其他敏感内容。</p>
        </div>
        <Link className="metrics-back" href="/">返回 Today</Link>
      </div>
      <section className="metrics-controls" aria-label="指标查询条件">
        <label>管理员令牌<input type="password" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" /></label>
        <label>开始日期<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
        <label>结束日期<input type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label>
        <label>事件平台<select value={platform} onChange={(event) => setPlatform(event.target.value)}>
          <option value="">全部平台</option><option value="android">Android</option><option value="ios">iOS</option><option value="web">Web</option><option value="unknown">未知</option>
        </select></label>
        <label>应用版本<input value={appVersion} onChange={(event) => setAppVersion(event.target.value)} placeholder="例如 0.1.0" /></label>
        <label>事件契约<select value={eventSchemaVersion} onChange={(event) => setEventSchemaVersion(event.target.value)}>
          <option value="">全部版本</option><option value="1">schema 1</option><option value="2">schema 2</option><option value="3">schema 3</option>
        </select></label>
        <div className="metrics-actions">
          <button type="button" onClick={() => void load()} disabled={busy || !token.trim()}>{busy ? "读取中…" : "读取指标"}</button>
          <button type="button" className="metrics-secondary" onClick={downloadMetrics} disabled={!metrics}>导出 JSON</button>
        </div>
      </section>
      {error ? <p className="metrics-error" role="alert">{error}</p> : null}
      {metrics ? <>
        <p className="metrics-scope">当前日期范围：{from} 至 {to}。平台和版本筛选只作用于事件质量明细；用户漏斗和留存仍按日期范围汇总。</p>
        <section className="metrics-grid" aria-label="激活漏斗">
          <MetricCard label="注册用户" value={metrics.activation.funnel.registeredUsers} />
          <MetricCard label="完成激活" value={metrics.activation.funnel.activatedUsers} />
          <MetricCard label="使用 Chat" value={metrics.activation.funnel.chatUsers} />
          <MetricCard label="产生价值" value={metrics.activation.funnel.valueUsers} />
          <MetricCard label="北极星用户" value={metrics.activation.funnel.northStarUsers} />
          <MetricCard label="保存记录" value={metrics.activation.funnel.checkinsSaved} />
          <MetricCard label="记录事件" value={metrics.activation.funnel.checkinSavedEvents} />
          <MetricCard label="D1 留存用户数" value={metrics.retention.cohort.d1} />
          <MetricCard label="D3 留存用户数" value={metrics.retention.cohort.d3} />
          <MetricCard label="D7 留存用户数" value={metrics.retention.cohort.d7} />
        </section>
        <p className={reconciliationConsistent ? "metrics-reconcile ok" : "metrics-reconcile warning"}>
          记录事实与客户端事件差异：{eventDelta > 0 ? "+" : ""}{eventDelta}；用户级缺口：业务事实漏报 {checkinUsersMissingSavedEvent}，事件无事实 {savedEventUsersMissingCheckin}。{reconciliationConsistent ? "当前对账一致。" : "请区分事件漏报与真实使用变化。"}
        </p>
        <section className="metrics-panels">
          <MetricTable title="质量事件" rows={metrics.quality.events.map((item) => [
            [item.eventName, item.platform, item.appVersion ? `v${item.appVersion}` : "", item.eventSchemaVersion ? `schema ${item.eventSchemaVersion}` : ""].filter(Boolean).join(" · "),
            item.eventCount, item.userCount,
          ])} />
          <MetricTable title="导出/删除任务" rows={metrics.quality.dataRequests.map((item) => [`${item.type} · ${item.status}`, item.taskCount, "—"])} />
          <MetricTable title="语音任务" rows={metrics.quality.voiceTranscriptions.map((item) => [`${item.status}${item.errorCode ? ` · ${item.errorCode}` : ""}`, item.taskCount, "—"])} />
          <MetricTable title="试用反馈" rows={metrics.quality.feedback.map((item) => [item.kind, item.feedbackCount, item.userCount])} />
        </section>
      </> : <p className="metrics-empty">输入管理员令牌后读取指定日期范围的聚合数据。</p>}
    </main>
  );
}

function MetricCard({ label, value }: { label: string; value: number }) {
  return <article className="metric-card"><span>{label}</span><strong>{value.toLocaleString("zh-CN")}</strong></article>;
}

function MetricTable({ title, rows }: { title: string; rows: Array<[string, number, number | string]> }) {
  return <section className="metric-table"><h2>{title}</h2><table><thead><tr><th>项目</th><th>次数</th><th>用户</th></tr></thead><tbody>
    {rows.length ? rows.map((row) => <tr key={`${row[0]}-${row[1]}`}><td>{row[0]}</td><td>{row[1]}</td><td>{row[2]}</td></tr>) : <tr><td colSpan={3}>暂无数据</td></tr>}
  </tbody></table></section>;
}
