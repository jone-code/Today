"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { ProactivePromptList } from "@/features/proactive/ProactivePromptList";
import { SummaryBlock } from "@/features/summary/SummaryBlock";
import { useToday } from "@/shared/lib/today-context";

export function TodayComposer() {
  const {
    todayEntry,
    prompts,
    saveToday,
    ready,
    mode,
    error,
    loopPhase,
    loopMessage,
    retryProcessing,
    retrySubmit,
    refreshToday,
  } = useToday();
  const [text, setText] = useState("");
  const [justSaved, setJustSaved] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [retrying, setRetrying] = useState(false);

  const busy = loopPhase === "submitting" || loopPhase === "processing";

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim() || busy) return;
    const payload = text;
    setJustSaved(true);
    setText("");
    setSaveError("");

    try {
      await saveToday(payload);
    } catch (err) {
      setJustSaved(false);
      setText(payload);
      setSaveError(err instanceof Error ? err.message : "保存失败");
    }
  };

  const onRetryProcessing = async () => {
    setRetrying(true);
    setSaveError("");
    try {
      await retryProcessing();
      setJustSaved(true);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "重试失败");
    } finally {
      setRetrying(false);
    }
  };

  const onRetrySubmit = async () => {
    setRetrying(true);
    setSaveError("");
    try {
      const entry = await retrySubmit();
      if (entry) {
        setJustSaved(true);
        setText("");
      }
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "重试失败");
    } finally {
      setRetrying(false);
    }
  };

  if (!ready) {
    return <p className="muted loading-line">正在唤醒记忆…</p>;
  }

  if (mode === "guest") {
    return (
      <div className="empty-state reveal">
        <p>登录后，Today 才能真正记住你的每一天。</p>
        <p className="muted">
          <Link href="/login">登录</Link> 或 <Link href="/register">注册</Link>
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="empty-state reveal">
        <p className="form-error">{error}</p>
        <button
          type="button"
          className="text-btn"
          onClick={() => void refreshToday()}
        >
          重新加载
        </button>
      </div>
    );
  }

  const processing =
    loopPhase === "processing" || todayEntry?.summaryStatus === "processing";
  const showResult =
    todayEntry &&
    (justSaved || text.length === 0 || processing || loopPhase === "error");

  if (todayEntry && !justSaved && text.length === 0 && !processing && loopPhase !== "error") {
    return (
      <div className="today-result reveal">
        <p className="eyebrow">今日已留下</p>
        <SummaryBlock entry={todayEntry} />
        <button
          type="button"
          className="text-btn"
          onClick={() => {
            setText(todayEntry.rawText);
            setJustSaved(true);
          }}
        >
          补充或改写今天
        </button>
      </div>
    );
  }

  return (
    <div className="composer-stack">
      <ProactivePromptList
        prompts={prompts}
        onSelect={(p, scaffold) => {
          setText((prev) => {
            const base = scaffold || "";
            if (!prev.trim()) return base;
            if (!base) return prev;
            return `${prev.trim()}\n${base}`;
          });
          setJustSaved(true);
        }}
      />

      <form onSubmit={onSubmit} className="composer reveal-delay">
        <label htmlFor="today-input" className="question">
          今天怎么样？
        </label>
        <textarea
          id="today-input"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="用几句话留下今天。不需要写得很完整。"
          rows={5}
          maxLength={2000}
          disabled={busy}
        />
        {(saveError || loopMessage) && (
          <p className={loopPhase === "error" || saveError ? "form-error" : "loop-status"}>
            {saveError || loopMessage}
          </p>
        )}
        {loopPhase === "error" ? (
          <div className="composer-actions">
            <span className="hint">可以重试整理，或改写后再提交</span>
            <div className="action-pair">
              {todayEntry?.summaryStatus === "processing" ? (
                <button
                  type="button"
                  className="btn-primary"
                  disabled={retrying}
                  onClick={() => void onRetryProcessing()}
                >
                  {retrying ? "重试中…" : "继续整理"}
                </button>
              ) : (
                <button
                  type="button"
                  className="btn-primary"
                  disabled={retrying}
                  onClick={() => void onRetrySubmit()}
                >
                  {retrying ? "重试中…" : "重新提交"}
                </button>
              )}
            </div>
          </div>
        ) : (
          <div className="composer-actions">
            <span className="hint">
              {loopPhase === "processing"
                ? "先留下了，正在整理总结与记忆"
                : mode === "local"
                  ? "本地演示模式"
                  : "大约 30 秒就够"}
            </span>
            <button type="submit" disabled={!text.trim() || busy}>
              {loopPhase === "submitting"
                ? "留下中…"
                : loopPhase === "processing"
                  ? "整理中…"
                  : "留下今天"}
            </button>
          </div>
        )}
      </form>

      {showResult && todayEntry && (justSaved || processing || loopPhase === "error") && (
        <div className="today-result reveal">
          <p className="eyebrow">
            {processing ? "今日已留下 · 整理中" : "今日总结"}
          </p>
          <SummaryBlock entry={todayEntry} />
        </div>
      )}
    </div>
  );
}
