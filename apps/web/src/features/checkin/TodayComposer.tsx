"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { ProactivePromptList } from "@/features/proactive/ProactivePromptList";
import { SummaryBlock } from "@/features/summary/SummaryBlock";
import { useToday } from "@/shared/lib/today-context";

export function TodayComposer() {
  const { todayEntry, prompts, saveToday, ready, mode, error } = useToday();
  const [text, setText] = useState("");
  const [justSaved, setJustSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim() || saving) return;
    const payload = text;
    setJustSaved(true);
    setText("");
    setSaving(true);
    setSaveError("");

    try {
      await saveToday(payload);
    } catch (err) {
      setJustSaved(false);
      setText(payload);
      setSaveError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
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
    return <p className="form-error">{error}</p>;
  }

  if (todayEntry && !justSaved && text.length === 0) {
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

  const showingResult = todayEntry && (justSaved || text.length === 0);

  return (
    <div className="composer-stack">
      <ProactivePromptList prompts={prompts} />

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
        />
        {saveError ? <p className="form-error">{saveError}</p> : null}
        <div className="composer-actions">
          <span className="hint">
            {mode === "local" ? "本地演示模式" : "大约 30 秒就够"}
          </span>
          <button type="submit" disabled={!text.trim() || saving}>
            {saving ? "记住中…" : "留下今天"}
          </button>
        </div>
      </form>

      {showingResult && todayEntry && justSaved && (
        <div className="today-result reveal">
          <p className="eyebrow">今日总结</p>
          <SummaryBlock entry={todayEntry} />
        </div>
      )}
    </div>
  );
}
