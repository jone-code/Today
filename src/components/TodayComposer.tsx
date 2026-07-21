"use client";

import { useState, type FormEvent } from "react";
import { moodEmoji } from "@/lib/ai";
import { useToday } from "@/lib/today-context";

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
      {prompts.length > 0 && (
        <div className="prompt-list reveal">
          {prompts.map((p) => (
            <p key={p.id} className="prompt-line">
              {p.text}
            </p>
          ))}
        </div>
      )}

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

function SummaryBlock({
  entry,
}: {
  entry: NonNullable<ReturnType<typeof useToday>["todayEntry"]>;
}) {
  const { summary } = entry;
  return (
    <div className="summary">
      <p className="one-liner">{summary.oneLiner}</p>
      <div className="summary-grid">
        <div>
          <h3>完成</h3>
          <ul>
            {summary.completed.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
        <div>
          <h3>情绪</h3>
          <p className="mood">
            <span aria-hidden>{moodEmoji[summary.mood]}</span> {summary.moodLabel}
          </p>
        </div>
      </div>
      <div className="keywords">
        <h3>今日关键词</h3>
        <p>{summary.keywords.join(" · ")}</p>
      </div>
    </div>
  );
}
