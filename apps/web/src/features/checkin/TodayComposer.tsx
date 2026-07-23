"use client";

import { useState, type FormEvent } from "react";
import { ProactivePromptList } from "@/features/proactive/ProactivePromptList";
import { SummaryBlock } from "@/features/summary/SummaryBlock";
import { useToday } from "@/shared/lib/today-context";

export function TodayComposer() {
  const { todayEntry, prompts, saveToday, ready } = useToday();
  const [text, setText] = useState("");
  const [justSaved, setJustSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim() || saving) return;
    const payload = text;
    setJustSaved(true);
    setText("");
    setSaving(true);

    try {
      await saveToday(payload);
    } catch {
      // 失败时回滚编辑状态（demo 场景）
      setJustSaved(false);
      setText(payload);
    } finally {
      setSaving(false);
    }
  };

  if (!ready) {
    return <p className="muted loading-line">正在唤醒记忆…</p>;
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
        <div className="composer-actions">
          <span className="hint">大约 30 秒就够</span>
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
